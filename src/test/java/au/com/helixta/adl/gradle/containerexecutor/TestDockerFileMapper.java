package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class TestDockerFileMapper
{
    private static DockerClient docker;
    private static ObjectFactory objectFactory;
    private static ArchiveProcessor archiveProcessor;

    /**
     * Give all created containers a specific but random-suffixed name so that if they end up not being cleaned up we can identity them.
     */
    private final String testDockerContainerName = "test-adl-gradle-plugin" + new Random().nextLong();

    /**
     * Tracks the IDs of all created Docker container so we can clean them up after test run.
     */
    private final List<String> createdContainerIds = new ArrayList<>();

    @BeforeAll
    private static void setUpDocker()
    {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        docker = DockerClientImpl.getInstance(config, httpClient);
    }

    @BeforeAll
    private static void setUpGradleEnvironment(@TempDir File tempDir)
    {
        Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
        objectFactory = p.getObjects();
        InjectReceiver injectReceiver = objectFactory.newInstance(InjectReceiver.class);
        archiveProcessor = new ArchiveProcessor(injectReceiver.archiveOperations);
    }

    /**
     * Due to a pretty bad bug in JUnit5, sometimes tempdirs get reused across tests despite the doco explicitly saying this is not the case.
     * So generate unique file names for each test to work around this nonsense.
     */
    private static Path createDockerBaseDirectory(Path root)
    throws IOException
    {
        return Files.createTempDirectory(root, "dockerbase");
    }

    /**
     * Purely for exercising Gradle's injection system to get objects.
     */
    public static class InjectReceiver
    {
        public final ArchiveOperations archiveOperations;

        @Inject
        public InjectReceiver(ArchiveOperations archiveOperations)
        {
            this.archiveOperations = archiveOperations;
        }
    }

    @AfterAll
    private static void closeDocker()
    throws IOException
    {
        if (docker != null)
            docker.close();
    }

    /**
     * Remove any containers we created during the tests.
     */
    @AfterEach
    private void cleanUpTestDockerContainer()
    {
        for (String createdContainerId : createdContainerIds)
        {
            docker.removeContainerCmd(createdContainerId).withRemoveVolumes(true).exec();
        }
    }

    /**
     * Check that generated command lines perform proper path mapping.
     */
    @Test
    void mappedCommandLine(@TempDir Path tempDir)
    {
        PreparedCommandLine commandLine = new PreparedCommandLine()
                                                .argument("-l")
                                                .argument(tempDir.toFile(), "mydir", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> mappedCommandLine = mapper.getMappedCommandLine();

        assertThat(mappedCommandLine).containsExactly("-l", "/data/mydir");
    }

    /**
     * Check that copying files from host works by doing an ls -l on a mapped directory and checking the output.  Checks file names but not content.
     */
    @Test
    void copyFilesFromHostFileNameCheck(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        //Put a couple of files in dir for copying across
        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));

        //Generate a command line for ls -a <mydir> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("-a")
                .argument(dockerBase.toFile(), "mydir", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("ls");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        assertThat(dockerLog.getLogContentAsLines()).contains(".", "..", "galah1.txt", "galah2.txt");
    }

    /**
     * Check that copying files from host works by doing grep with a directory and checking the output.
     */
    @Test
    void copyFilesFromHostContentCheck(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        //Put a couple of files in dir for copying across
        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));

        //Generate a command line for grep -r other <mapped dir>
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("-r")
                .argument("other")
                .argument(dockerBase.toFile(), "mydir", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("grep");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        //Grep should only find 2nd file and this also tests the full path of the mapped file is generated properly
        assertThat(dockerLog.getLogContentAsLines()).containsExactly("/data/mydir/galah2.txt:Another file");
    }

    @Test
    void copyFilesFromContainer(@TempDir Path tempDir)
    throws IOException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        //Generate a command line for cp /etc/shells <mydir> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("/etc/shells")
                .argument(dockerBase.toFile(), "mydir", PreparedCommandLine.FileTransferMode.OUTPUT, PreparedCommandLine.FileType.DIRECTORY);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("cp");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();

        mapper.copyFilesFromContainerToHost(containerId);

        //Verify files were created on host and have correct content
        Path shellsFile = dockerBase.resolve("shells");
        assertThat(shellsFile).exists();
        List<String> shellsFileLines = Files.readAllLines(shellsFile);
        assertThat(shellsFileLines).contains("/bin/bash", "/bin/sh"); //File will have more than these, but bash and sh should always be there
    }

    /**
     * Check registering and copying a single file works.
     */
    @Test
    void copySingleFileFromHost(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        //A single file to copy across
        Path inputFile = dockerBase.resolve("galah1.txt");
        Files.write(inputFile, ImmutableList.of("This is file content"));

        //Generate a command line for cat <myfile> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument(inputFile.toFile(), "myfile.txt", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.SINGLE_FILE);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("cat");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        assertThat(dockerLog.getLogContentAsLines()).contains("This is file content");
    }

    /**
     * Check registering and copying a single output file works.
     */
    @Test
    void copySingleFileFromContainer(@TempDir Path tempDir)
    throws IOException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        Path shellsFile = dockerBase.resolve("shellsfile.txt");

        //Generate a command line for cp /etc/shells <mapped file>
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("/etc/shells")
                .argument(shellsFile.toFile(), "shellsfile.txt", PreparedCommandLine.FileTransferMode.OUTPUT, PreparedCommandLine.FileType.SINGLE_FILE);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("cp");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();

        mapper.copyFilesFromContainerToHost(containerId);

        //Verify files were created on host and have correct content
        assertThat(shellsFile).exists();
        List<String> shellsFileLines = Files.readAllLines(shellsFile);
        assertThat(shellsFileLines).contains("/bin/bash", "/bin/sh"); //File will have more than these, but bash and sh should always be there
    }

    /**
     * Register an input and output file type and see if it copied bi-directionally.
     */
    @Test
    void copyFilesInputAndOutput(@TempDir Path tempDir)
    throws IOException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);
        Path scriptFile = dockerBase.resolve("myscript.sh");
        Path ioFile = dockerBase.resolve("thefile.txt");

        //Prepare script
        String scriptContent =
                "#!/bin/bash\n" +
                "echo \"Added in container\" >> $1\n";
        Files.write(scriptFile, scriptContent.getBytes(StandardCharsets.UTF_8));

        //Prepare I/O file
        Files.write(ioFile, "One\nTwo\nThree\n".getBytes(StandardCharsets.UTF_8));

        //Generate a command line for /bin/bash <input script> <i/o file>
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument(scriptFile.toFile(), "myscript.sh", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.SINGLE_FILE)
                .argument(ioFile.toFile(), "thefile.txt", PreparedCommandLine.FileTransferMode.INPUT_OUTPUT, PreparedCommandLine.FileType.SINGLE_FILE);
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("/bin/bash");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();

        mapper.copyFilesFromContainerToHost(containerId);

        //Verify input file was correctly appended
        assertThat(ioFile).exists();
        List<String> ioFileLines = Files.readAllLines(ioFile);
        assertThat(ioFileLines).contains("One", "Two", "Three", "Added in container");
    }

    @Test
    void copyInputFileTreeUsingBaseDirectory(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));
        Path file3 = dockerBase.resolve("cockatoo1.txt");
        Files.write(file3, ImmutableList.of("Cockatoo file"));

        //Put a couple of files in dir for copying across
        FileTree tree = objectFactory.fileTree()
                                     .from(dockerBase)
                                     .filter(f -> f.getName().endsWith("1.txt"))
                                     .getAsFileTree();

        //Generate a command line for ls -a <mydir> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("-a")
                .argument(tree, "mydir", new PreparedCommandLine.SingleBaseDirectoryCommandLineGenerator());
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("ls");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        assertThat(dockerLog.getLogContentAsLines()).contains(".", "..", "galah1.txt", "cockatoo1.txt");
    }

    @Test
    void copyInputFileTreeNestedDirectoryStructureUsingBaseDirectory(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path subdir = dockerBase.resolve("sub");
        Files.createDirectories(subdir);
        Path file2 = subdir.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));
        Path file3 = subdir.resolve("cockatoo1.txt");
        Files.write(file3, ImmutableList.of("Cockatoo file"));

        //Put a couple of files in dir for copying across
        FileTree tree = objectFactory.fileTree()
                                     .from(dockerBase)
                                     .getAsFileTree();

        //Generate a command line for find <mydir> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument(tree, "mydir", new PreparedCommandLine.SingleBaseDirectoryCommandLineGenerator());
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("find");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        //Each line will be an absolute path from the output of the find command
        assertThat(dockerLog.getLogContentAsLines()).contains(
                "/data/mydir",
                "/data/mydir/galah1.txt",
                "/data/mydir/sub",
                "/data/mydir/sub/cockatoo1.txt",
                "/data/mydir/sub/galah2.txt");
    }

    @Test
    void copyInputFileTreeUsingMultipleArguments(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));
        Path file3 = dockerBase.resolve("cockatoo1.txt");
        Files.write(file3, ImmutableList.of("Cockatoo file"));

        //Put a couple of files in dir for copying across
        FileTree tree = objectFactory.fileTree()
                                     .from(dockerBase)
                                     .filter(f -> f.getName().endsWith("1.txt"))
                                     .getAsFileTree();

        //Generate a command line for cat <files...> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument(tree, "mydir");
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("cat");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        //3rd file is ignored in the file tree filter
        assertThat(dockerLog.getLogContentAsLines()).containsExactlyInAnyOrder("Cockatoo file", "This is file content");
    }

    @Test
    void copyInputFileTreeNestedDirectoryStructureUsingMultipleArguments(@TempDir Path tempDir)
    throws IOException, InterruptedException
    {
        //The directory all our transferred files will come from - don't use tempDir directly because it is also being used for Gradle fake project dir
        Path dockerBase = createDockerBaseDirectory(tempDir);

        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path subdir = dockerBase.resolve("sub");
        Files.createDirectories(subdir);
        Path file2 = subdir.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));
        Path file3 = subdir.resolve("cockatoo1.txt");
        Files.write(file3, ImmutableList.of("Cockatoo file"));

        //Put a couple of files in dir for copying across
        FileTree tree = objectFactory.fileTree()
                                     .from(dockerBase)
                                     .getAsFileTree();

        //Generate a command line for cat <files...> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument(tree, "mydir");
        DockerFileMapper mapper = new DockerFileMapper(commandLine, "/data", docker, objectFactory, archiveProcessor);

        List<String> fullCommandLine = mapper.getMappedCommandLineWithProgram("cat");

        //Create a basic Linux container
        CreateContainerResponse response = docker.createContainerCmd("ubuntu:20.04")
                                                 .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                                 .withName(testDockerContainerName)
                                                 .withCmd(fullCommandLine)
                                                 .exec();
        String containerId = response.getId();
        createdContainerIds.add(containerId);

        mapper.copyFilesFromHostToContainer(containerId);

        docker.startContainerCmd(containerId).exec();

        WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
        Integer result = resultCallback.awaitStatusCode();
        assertThat(result).describedAs("Docker process exit code").isZero();
        DockerLogCollector dockerLog = new DockerLogCollector();
        docker.logContainerCmd(containerId).withStdOut(true).exec(dockerLog).awaitCompletion();

        assertThat(dockerLog.getLogContentAsLines()).containsExactlyInAnyOrder("Cockatoo file", "This is file content", "Another file");
    }

    /**
     * Callback for reading Docker logs.
     */
    private static class DockerLogCollector extends ResultCallback.Adapter<Frame>
    {
        private final StringBuffer buf = new StringBuffer();

        @Override
        public void onNext(Frame object)
        {
            String value = new String(object.getPayload(), StandardCharsets.UTF_8);
            buf.append(value);
        }

        /**
         * @return all log content as one string.
         */
        public String getLogContent()
        {
            return buf.toString();
        }

        /**
         * @return all log content split into lines.
         */
        public List<String> getLogContentAsLines()
        {
            try (BufferedReader r = new BufferedReader(new StringReader(getLogContent())))
            {
                return r.lines().collect(Collectors.toList());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
