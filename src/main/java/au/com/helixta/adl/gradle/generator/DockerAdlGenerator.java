package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.AdlPluginExtension;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DockerAdlGenerator implements AdlGenerator
{
    private static final Logger log = Logging.getLogger(DockerAdlGenerator.class);

    private static final String TOOL_ADLC = "adlc";

    private final AdlToolLogger adlLog;
    private final DockerClient docker;
    private final ObjectFactory objectFactory;

    public DockerAdlGenerator(DockerClient docker, AdlToolLogger adlLog, ObjectFactory objectFactory)
    {
        this.docker = Objects.requireNonNull(docker);
        this.adlLog = Objects.requireNonNull(adlLog);
        this.objectFactory = Objects.requireNonNull(objectFactory);
    }

    public static DockerAdlGenerator fromConfiguration(AdlToolLogger adlLog, ObjectFactory objectFactory) //TODO input config
    {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                                            .dockerHost(config.getDockerHost())
                                            .sslConfig(config.getSSLConfig())
                                            .build();

         DockerClient docker = DockerClientImpl.getInstance(config, httpClient);
         return new DockerAdlGenerator(docker, adlLog, objectFactory);
    }

    private void pullDockerImage(String imageName)
    throws AdlGenerationException
    {
        adlLog.info(TOOL_ADLC, "Pulling docker image " + imageName + "...");
        ResultCallback.Adapter<PullResponseItem> pullResponse = docker.pullImageCmd(imageName).start();
        try
        {
            pullResponse.awaitCompletion(); //TODO timeouts
        }
        catch (InterruptedException e)
        {
            throw new AdlGenerationException("Interrupted waiting for Docker pull.", e);
        }
        adlLog.info(TOOL_ADLC, "Docker image downloaded.");
    }

    private void checkPullDockerImage(String imageName)
    throws AdlGenerationException
    {
        List<Image> images = docker.listImagesCmd().withImageNameFilter(imageName).exec();
        if (images.isEmpty())
            pullDockerImage(imageName);
    }

    protected String containerName()
    {
        return "adl";
    }

    protected String imageName()
    {
        return "helixta/hxadl:0.31.1"; //TODO parameterize version
    }

    private SourceTarArchive createTarFromFileTree(FileTree sources, String basePath)
    throws AdlGenerationException
    {
        List<String> filesInContainer = new ArrayList<>();

        //Generate TAR archive
        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            sources.visit(fileVisitDetails ->
            {
                String tarEntryFilePath = basePath + fileVisitDetails.getRelativePath().getPathString();
                if (fileVisitDetails.isDirectory() && !tarEntryFilePath.endsWith("/")) //TAR library makes anything ending with '/' a directory
                    tarEntryFilePath = tarEntryFilePath + "/";

                TarArchiveEntry tarEntry = new TarArchiveEntry(tarEntryFilePath);

                tarEntry.setModTime(fileVisitDetails.getLastModified());
                if (!fileVisitDetails.isDirectory())
                    tarEntry.setSize(fileVisitDetails.getSize());

                try
                {
                    tarOs.putArchiveEntry(tarEntry);
                    if (!fileVisitDetails.isDirectory())
                    {
                        filesInContainer.add(tarEntryFilePath);
                        try (InputStream entryFileIs = fileVisitDetails.open())
                        {
                            IOUtils.copy(entryFileIs, tarOs);
                        }
                    }
                    tarOs.closeArchiveEntry();
                }
                catch (IOException e)
                {
                    throw new AdlGenerationRuntimeException(new AdlGenerationException("Error generating TAR file with ADL source files: " + e.getMessage(), e));
                }
            });
        }
        catch (IOException e)
        {
            throw new AdlGenerationException("Error generating TAR file with ADL source files: " + e.getMessage(), e);
        }
        catch (AdlGenerationRuntimeException e)
        {
            throw e.getCheckedException();
        }

        return new SourceTarArchive(new ByteArrayInputStream(tarBos.toByteArray()), basePath, filesInContainer);
    }

    private void copySourceFilesToDockerContainer(SourceTarArchive sources, String dockerContainerId)
    {
        docker.copyArchiveToContainerCmd(dockerContainerId)
              .withRemotePath("/") //All paths in TAR are absolute for the container
              .withTarInputStream(sources.getInputStream())
              .exec();
    }

    private List<String> adlcCommand(AdlPluginExtension.Generation generation, SourceTarArchive sources, List<? extends SourceTarArchive> searchDirs)
    throws AdlGenerationException
    {
        if (generation instanceof AdlPluginExtension.JavaGeneration)
            return adlcJavaCommand((AdlPluginExtension.JavaGeneration)generation, sources, searchDirs);
        else
            throw new AdlGenerationException("Unknown generation type: " + generation.getClass().getName());
    }

    private List<String> adlcJavaCommand(AdlPluginExtension.JavaGeneration generation, SourceTarArchive sources, List<? extends SourceTarArchive> searchDirs)
    {
        List<String> command = new ArrayList<>();
        command.add("/opt/bin/adlc");
        command.add("java");

        command.add("--outputdir=" + getOutputPathInContainer());

        for (SourceTarArchive searchDir : searchDirs)
        {
            command.add("--searchdir=" + searchDir.getBaseDirectory());
        }

        if (generation.getJavaPackage() != null && !generation.getJavaPackage().trim().isEmpty())
            command.add("--package=" + generation.getJavaPackage());

        if (generation.isVerbose())
            command.add("--verbose");

        command.addAll(sources.getFilePaths());

        return command;
    }

    protected String getSourcePathInContainer()
    {
        return "/data/sources/";
    }

    protected String getOutputPathInContainer()
    {
        return "/data/generated/";
    }

    protected String getSearchDirectoryPathInContainer()
    {
        return "/data/searchdirs/";
    }

    private void runAdlc(AdlPluginExtension.Generation generation)
    throws AdlGenerationException
    {
        //Generate archive for source files
        SourceTarArchive sourceTar = createTarFromFileTree(generation.getSourcepath(), getSourcePathInContainer());

        //and searchdirs
        List<SourceTarArchive> searchDirTars = new ArrayList<>();
        int searchDirIndex = 0;
        for (File searchDirectory : generation.getSearchDirectories())
        {
            String searchDirContainerPath = getSearchDirectoryPathInContainer() + searchDirIndex + "/";
            ConfigurableFileTree searchDirTree = objectFactory.fileTree().from(searchDirectory);
            SourceTarArchive searchDirTar = createTarFromFileTree(searchDirTree, searchDirContainerPath);
            searchDirTars.add(searchDirTar);
            searchDirIndex++;
        }

        //Put together the ADL command
        List<String> adlcCommand = adlcCommand(generation, sourceTar, searchDirTars);
        log.info("adlc command " + adlcCommand);

        //Create Docker container that can execute ADL compiler
        String containerName = containerName();
        CreateContainerResponse c = docker.createContainerCmd(imageName())
                                          .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                          .withName(containerName)
                                          .withCmd(adlcCommand)
                                          .exec();
        String containerId = c.getId();


        try
        {
            //Copy source files to the container
            copySourceFilesToDockerContainer(sourceTar, containerId);
            for (SourceTarArchive searchDirTar : searchDirTars)
            {
                copySourceFilesToDockerContainer(searchDirTar, containerId);
            }

            //Reading console output from the process
            //Don't log directly from the callback because it's on another thread and Gradle has a threadlocal to group task-specific logs
            List<ConsoleRecord> adlConsoleRecords = Collections.synchronizedList(new ArrayList<>());
            ResultCallbackTemplate<ResultCallback<Frame>, Frame> outCallback = docker.attachContainerCmd(containerId)
                                                                                     .withStdOut(true).withStdErr(true)
                                                                                     .withFollowStream(true)
                                                                                     .exec(new ResultCallbackTemplate<ResultCallback<Frame>, Frame>()
                                                                                     {
                                                                                         @Override
                                                                                         public void onNext(Frame object)
                                                                                         {
                                                                                             adlConsoleRecords.add(new ConsoleRecord(object.getStreamType(),
                                                                                                                                     new String(object.getPayload(),
                                                                                                                                                StandardCharsets.UTF_8)));
                                                                                         }
                                                                                     });
            try
            {
                //Required since HTTP is async and we need this to be run and attached before container really starts
                //See https://github.com/docker-java/docker-java/issues/1492
                //https://github.com/docker-java/docker-java/pull/1494
                outCallback.awaitStarted();
            }
            catch (InterruptedException e)
            {
                throw new AdlGenerationException("Interrupted waiting for console output.", e);
            }

            docker.startContainerCmd(containerId).exec();

            Integer result = docker.waitContainerCmd(containerId).start().awaitStatusCode(); //TODO timeout
            boolean hasErrors = (result == null || result != 0);

            //Stream console records to logger now
            for (ConsoleRecord adlConsoleRecord : adlConsoleRecords)
            {
                switch (adlConsoleRecord.getType())
                {
                    case STDOUT:
                        //adlc puts error messages to stdout, so in case of error send them all to error
                        if (hasErrors)
                            adlLog.error(TOOL_ADLC, adlConsoleRecord.getMessage());
                        else
                            adlLog.info(TOOL_ADLC, adlConsoleRecord.getMessage());
                        break;
                    case STDERR:
                        adlLog.error(TOOL_ADLC, adlConsoleRecord.getMessage());
                        break;
                    //Ignore other types, stdout/stderr is only two we are interested in
                }
            }

            if (hasErrors)
                throw new AdlGenerationException("adlc error (" + result + ")");

            //Copy generated files back out of container
            int generatedAdlFileCount = copyOutputFilesFromDockerContainer(generation, containerId);
            log.info(generatedAdlFileCount + " ADL file(s) generated.");
        }
        finally
        {
            try
            {
                docker.removeContainerCmd(containerId).withRemoveVolumes(true).exec();
            }
            catch (DockerException e)
            {
                //Log the error but don't fail the build since there might be another exception that occurred beforehand and we
                //don't want to clobber it
                log.error("Error removing adlc Docker container " + containerId + ": " + e.getMessage(), e);
            }
        }
    }

    private int copyOutputFilesFromDockerContainer(AdlPluginExtension.Generation generation, String containerId)
    throws AdlGenerationException
    {
        int generatedAdlFileCount = 0;
        Directory outDir = generation.getOutputDirectory().get();

        try (InputStream is = docker.copyArchiveFromContainerCmd(containerId, getOutputPathInContainer()).exec();
             TarArchiveInputStream tis = new TarArchiveInputStream(is))
        {
            TarArchiveEntry entry;
            do
            {
                entry = tis.getNextTarEntry();
                if (entry != null && !entry.isDirectory())
                {
                    //Docker TAR archives have the last segment of the base directory in the TAR archive, so strip that out
                    String relativeName = relativizeTarPath(getOutputPathInContainer(), entry.getName());

                    //Copy the file data to the host filesystem
                    File adlOutputFile = outDir.file(relativeName).getAsFile();
                    FileUtils.forceMkdirParent(adlOutputFile);
                    Files.copy(tis, adlOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    generatedAdlFileCount++;
                }
            }
            while (entry != null);
        }
        catch (IOException e)
        {
            throw new AdlGenerationException(e);
        }

        return generatedAdlFileCount;
    }

    private static String relativizeTarPath(String expectedBase, String entryName)
    {
        //Docker puts the last segment of the base in the TAR entry, so remove it
        String lastBasePathSegment = RelativePath.parse(false, expectedBase).getLastName();

        RelativePath entryPath = RelativePath.parse(true, entryName);

        if (entryPath.getSegments()[0].equals(lastBasePathSegment))
        {
            RelativePath adjustedEntryPath = new RelativePath(true, Arrays.copyOfRange(entryPath.getSegments(), 1, entryPath.getSegments().length));
            return adjustedEntryPath.getPathString();
        }
        else //No adjustment needed
            return entryName;
    }

    @Override
    public void generate(Iterable<? extends AdlPluginExtension.Generation> generations)
    throws AdlGenerationException
    {
        //Check if the image exists, if not then pull it
        checkPullDockerImage(imageName());

        for (AdlPluginExtension.Generation generation : generations)
        {
            runAdlc(generation);
        }
    }

    @Override
    public void close() throws IOException
    {
        docker.close();
    }

    private static class AdlGenerationRuntimeException extends RuntimeException
    {
        private final AdlGenerationException checkedException;

        public AdlGenerationRuntimeException(AdlGenerationException cause)
        {
            super(cause);
            this.checkedException = Objects.requireNonNull(cause);
        }

        public AdlGenerationException getCheckedException()
        {
            return checkedException;
        }
    }

    private static class ConsoleRecord
    {
        private final StreamType type;
        private final String message;

        public ConsoleRecord(StreamType type, String message)
        {
            this.type = type;
            this.message = message;
        }

        public StreamType getType()
        {
            return type;
        }

        public String getMessage()
        {
            return message;
        }
    }

    private static class SourceTarArchive implements Closeable
    {
        private final InputStream inputStream;
        private final String baseDirectory;
        private final List<String> filePaths;

        public SourceTarArchive(InputStream inputStream, String baseDirectory, List<String> filePaths)
        {
            this.inputStream = inputStream;
            this.baseDirectory = baseDirectory;
            this.filePaths = new ArrayList<>(filePaths);
        }

        public InputStream getInputStream()
        {
            return inputStream;
        }

        public String getBaseDirectory()
        {
            return baseDirectory;
        }

        public List<String> getFilePaths()
        {
            return filePaths;
        }

        @Override
        public void close()
        throws IOException
        {
            inputStream.close();
        }
    }
}
