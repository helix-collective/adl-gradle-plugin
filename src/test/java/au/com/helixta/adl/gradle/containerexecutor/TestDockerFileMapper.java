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
                                                .argument(tempDir.toFile(), "mydir", PreparedCommandLine.FileMode.INPUT);
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
        Path dockerBase = tempDir.resolve("dockerbase");
        Files.createDirectories(dockerBase);

        //Put a couple of files in dir for copying across
        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));

        //Generate a command line for ls -a <mydir> which is mapped
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("-a")
                .argument(dockerBase.toFile(), "mydir", PreparedCommandLine.FileMode.INPUT);
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
        Path dockerBase = tempDir.resolve("dockerbase");
        Files.createDirectories(dockerBase);

        //Put a couple of files in dir for copying across
        Path file1 = dockerBase.resolve("galah1.txt");
        Files.write(file1, ImmutableList.of("This is file content"));
        Path file2 = dockerBase.resolve("galah2.txt");
        Files.write(file2, ImmutableList.of("Another file"));

        //Generate a command line for grep -r other <mapped dir>
        PreparedCommandLine commandLine = new PreparedCommandLine()
                .argument("-r")
                .argument("other")
                .argument(dockerBase.toFile(), "mydir", PreparedCommandLine.FileMode.INPUT);
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
