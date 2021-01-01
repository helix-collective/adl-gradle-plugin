package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.AdlPluginExtension;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.CreateContainerResponse;
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
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileTree;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DockerAdlGenerator implements AdlGenerator
{
    private static final String TOOL_ADLC = "adlc";

    private final AdlToolLogger adlLog;

    private final DockerClient docker;

    public DockerAdlGenerator(DockerClient docker, AdlToolLogger adlLog)
    {
        this.docker = Objects.requireNonNull(docker);
        this.adlLog = Objects.requireNonNull(adlLog);
    }

    public static DockerAdlGenerator fromConfiguration(AdlToolLogger adlLog) //TODO input config
    {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                                            .dockerHost(config.getDockerHost())
                                            .sslConfig(config.getSSLConfig())
                                            .build();

         DockerClient docker = DockerClientImpl.getInstance(config, httpClient);
         return new DockerAdlGenerator(docker, adlLog);
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

    private void copySourceFilesToDockerContainer(FileTree sources, String dockerContainerId, String dockerContainerTargetPath)
    throws AdlGenerationException
    {
        //Generate TAR archive
        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            sources.visit(fileVisitDetails ->
            {
                String tarPath = dockerContainerTargetPath + fileVisitDetails.getRelativePath().getPathString();
                if (fileVisitDetails.isDirectory() && !tarPath.endsWith("/")) //TAR library makes anything ending with '/' a directory
                    tarPath = tarPath + "/";

                TarArchiveEntry tarEntry = new TarArchiveEntry(tarPath);

                tarEntry.setModTime(fileVisitDetails.getLastModified());
                if (!fileVisitDetails.isDirectory())
                    tarEntry.setSize(fileVisitDetails.getSize());

                try
                {
                    tarOs.putArchiveEntry(tarEntry);
                    if (!fileVisitDetails.isDirectory())
                    {
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

        docker.copyArchiveToContainerCmd(dockerContainerId)
              .withRemotePath("/") //All paths in TAR are absolute for the container
              .withTarInputStream(new ByteArrayInputStream(tarBos.toByteArray()))
              .exec();
    }

    @Override
    public void generate(Iterable<? extends AdlPluginExtension.Generation> generations)
    throws AdlGenerationException
    {
        String imageName = "helixta/hxadl:0.31.1"; //TODO configure based on version, etc.

        //Check if the image exists, if not then pull it
        checkPullDockerImage(imageName);

        //Put together the ADL command
        //TODO

        //Create Docker container that can execute ADL compiler
        String containerName = containerName();
        CreateContainerResponse c = docker.createContainerCmd(imageName)
                                          .withHostConfig(HostConfig.newHostConfig().withAutoRemove(true))
                                          .withName(containerName)
                                          //.withCmd("/opt/bin/adlc")
                                          //.withCmd("sleep", "1000")
                                          .withCmd("/opt/bin/adlc", "show", "--version")
                                          //.withCmd("/opt/bin/adlc")
                                          //.withCmd("tar", "galah")
                                          .exec();
        String containerId = c.getId();

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
                                                                                         adlConsoleRecords.add(new ConsoleRecord(object.getStreamType(), new String(object.getPayload(), StandardCharsets.UTF_8)));
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

        //Stream console records to logger now
        for (ConsoleRecord adlConsoleRecord : adlConsoleRecords)
        {
            switch (adlConsoleRecord.getType())
            {
                case STDOUT:
                    adlLog.info(TOOL_ADLC, adlConsoleRecord.getMessage());
                    break;
                case STDERR:
                    adlLog.error(TOOL_ADLC, adlConsoleRecord.getMessage());
                    break;
                //Ignore other types, stdout/stderr is only two we are interested in
            }
        }

        if (result == null || result != 0)
            throw new AdlGenerationException("adlc error (" + result + ")");

        /*
        int generationIndex = 0;
        for (AdlPluginExtension.Generation generation : generations)
        {
            String sourcePathInContainer = "/data/gen" + generationIndex + "/sources/";
            copySourceFilesToDockerContainer(generation.getSourcepath(), containerId, sourcePathInContainer);

            generationIndex++;
        }

         */
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
}
