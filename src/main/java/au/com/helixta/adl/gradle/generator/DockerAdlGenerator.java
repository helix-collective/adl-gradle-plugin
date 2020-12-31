package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.AdlPluginExtension;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
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
import java.util.List;
import java.util.Objects;

public class DockerAdlGenerator implements AdlGenerator
{
    private final DockerClient docker;

    public DockerAdlGenerator(DockerClient docker)
    {
        this.docker = Objects.requireNonNull(docker);
    }

    public static DockerAdlGenerator fromConfiguration() //TODO input config
    {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                                            .dockerHost(config.getDockerHost())
                                            .sslConfig(config.getSSLConfig())
                                            .build();

         DockerClient docker = DockerClientImpl.getInstance(config, httpClient);
         return new DockerAdlGenerator(docker);
    }

    private void pullDockerImage(String imageName)
    throws AdlGenerationException
    {
        System.out.println("Downloading docker image");
        ResultCallback.Adapter<PullResponseItem> pullResponse = docker.pullImageCmd(imageName).start();
        try
        {
            pullResponse.awaitCompletion(); //TODO timeouts
        }
        catch (InterruptedException e)
        {
            throw new AdlGenerationException("Interrupted waiting for Docker pull.", e);
        }
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
        String imageName = "helixta/hxadl:0.31.1";

        //Check if the image exists, if not then pull it
        checkPullDockerImage(imageName);

        //Create Docker container that can execute ADL compiler
        String containerName = containerName();
        CreateContainerResponse c = docker.createContainerCmd(imageName)
                                          .withHostConfig(HostConfig.newHostConfig().withAutoRemove(true))
                                          .withName(containerName)
                                          //.withCmd("/opt/bin/adlc")
                                          .withCmd("sleep", "1000")
                                          .exec();
        String containerId = c.getId();



        int generationIndex = 0;
        for (AdlPluginExtension.Generation generation : generations)
        {
            String sourcePathInContainer = "/data/gen" + generationIndex + "/sources/";
            copySourceFilesToDockerContainer(generation.getSourcepath(), containerId, sourcePathInContainer);

            generationIndex++;
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
}
