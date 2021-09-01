package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.ImageBuildMode;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.DistributionService;
import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;
import au.com.helixta.adl.gradle.generator.AdlToolLogger;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultArchitecture;
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public class DockerExecutor implements ContainerExecutor
{
    private static final Logger log = Logging.getLogger(DockerExecutor.class);

    private static final String DOCKER_GENERATOR_LABEL = "au.com.helixta.adl.gradle.docker";
    private static final String TOOL_DOCKER = "docker";

    private final DockerClient docker;
    private final DistributionService distributionService;
    private final ExecutableResolver executableResolver;
    private final UnaryOperator<List<String>> commandLinePostProcessor;
    private final String dockerToolInstallBaseDirectory;
    private final String dockerMappedFileBaseDirectory;
    private final DockerImageDefinitionTransformer dockerImageDefinitionTransformer;
    private final String distributionVersion;
    private final String baseDockerImageName;
    private final String baseDockerContainerName;
    private final DockerConfiguration dockerConfiguration;
    private final AdlToolLogger adlLog;
    private final String logToolName;
    private final TargetMachineFactory targetMachineFactory;
    private final ObjectFactory objectFactory;
    private final ArchiveProcessor archiveProcessor;

    /**
     * Creates a Docker executor.
     *
     * @param docker Docker client.
     * @param distributionService distribution service for downloading and resolving the tool distribution that will be installed into Docker images.
     * @param executableResolver used for resolving the executable of the tool in the distribution.
     * @param commandLinePostProcessor transform / post process the command line specifically for Docker execution.
     * @param dockerToolInstallBaseDirectory base directory for installing the tool into the docker image.
     * @param dockerMappedFileBaseDirectory base directory in the Docker container for copying input/output files as part of the tool execution.
     * @param dockerImageDefinitionTransformer modifies the Dockerfile used to build the base image from the default if needed.
     * @param distributionVersion version of the distribution to use.
     * @param baseDockerImageName the base Docker image name to generate, without the version.
     * @param baseDockerContainerName the prefix used to name Docker containers generated from the executor.
     * @param dockerConfiguration Docker configuration.
     * @param adlLog tool logger.
     * @param logToolName name of the tool to use when logging.
     * @param targetMachineFactory target machine factory.
     * @param objectFactory Gradle object factory.
     * @param archiveProcessor Gradle archive processor object.
     */
    public DockerExecutor(DockerClient docker, DistributionService distributionService, ExecutableResolver executableResolver,
                          UnaryOperator<List<String>> commandLinePostProcessor,
                          String dockerToolInstallBaseDirectory, String dockerMappedFileBaseDirectory,
                          DockerImageDefinitionTransformer dockerImageDefinitionTransformer,
                          String distributionVersion, String baseDockerImageName, String baseDockerContainerName,
                          DockerConfiguration dockerConfiguration, AdlToolLogger adlLog, String logToolName,
                          TargetMachineFactory targetMachineFactory, ObjectFactory objectFactory, ArchiveProcessor archiveProcessor)
    {
        this.docker = Objects.requireNonNull(docker);
        this.distributionService = Objects.requireNonNull(distributionService);
        this.executableResolver = Objects.requireNonNull(executableResolver);
        this.commandLinePostProcessor = Objects.requireNonNull(commandLinePostProcessor);
        this.dockerToolInstallBaseDirectory = Objects.requireNonNull(dockerToolInstallBaseDirectory);
        this.dockerMappedFileBaseDirectory = Objects.requireNonNull(dockerMappedFileBaseDirectory);
        this.dockerImageDefinitionTransformer = Objects.requireNonNull(dockerImageDefinitionTransformer);
        this.distributionVersion = Objects.requireNonNull(distributionVersion);
        this.baseDockerImageName = Objects.requireNonNull(baseDockerImageName);
        this.baseDockerContainerName = Objects.requireNonNull(baseDockerContainerName);
        this.dockerConfiguration = Objects.requireNonNull(dockerConfiguration);
        this.adlLog = Objects.requireNonNull(adlLog);
        this.logToolName = Objects.requireNonNull(logToolName);
        this.targetMachineFactory = Objects.requireNonNull(targetMachineFactory);
        this.objectFactory = Objects.requireNonNull(objectFactory);
        this.archiveProcessor = Objects.requireNonNull(archiveProcessor);
    }

    protected String dockerImageName(String baseDockerImageName, String distributionVersion)
    {
        return baseDockerImageName + ":" + distributionVersion;
    }

    @Override
    public void execute(PreparedCommandLine commandLine)
    throws IOException, DistributionNotFoundException, ContainerExecutionException
    {
        //Resolve Docker image and build it if it does not already exist
        String dockerImageName = dockerImageName(baseDockerImageName, distributionVersion);
        boolean imageAvailable = checkPullDockerImage(dockerImageName);
        if (!imageAvailable)
        {
            log.info("Docker image '" + dockerImageName + "' not found in repository so it will be built.");
            buildDockerImage(dockerImageName, distributionVersion);
        }

        runTool(commandLine, dockerImageName);
    }

    private void runTool(PreparedCommandLine commandLine, String dockerImageName)
    throws ContainerExecutionException, IOException
    {
        DockerFileMapper dockerFileMapper = new DockerFileMapper(commandLine, dockerMappedFileBaseDirectory, docker, objectFactory, archiveProcessor);

        //Generate the command line string including mapped file names
        String toolExecutableFullPath = executableResolver.resolveExecutable(dockerToolInstallBaseDirectory, distributionSpecifierForDockerImage());
        List<String> toolCommand = dockerFileMapper.getMappedCommandLineWithProgram(toolExecutableFullPath);
        toolCommand = commandLinePostProcessor.apply(toolCommand);

        //Generate the container from the Docker image
        String containerName = generateDockerContainerName();
        CreateContainerResponse c = docker.createContainerCmd(dockerImageName)
                                          .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                          .withName(containerName)
                                          .withCmd(toolCommand)
                                          .exec();
        String containerId = c.getId();

        try
        {
            //Copy input and input/output files from host to container
            dockerFileMapper.copyFilesFromHostToContainer(containerId);

            //Reading console output from the process
            //Don't log directly from the callback because it's on another thread and Gradle has a threadlocal to group task-specific logs
            ConsoleRecorder adlConsoleRecords = new ConsoleRecorder();
            ResultCallbackTemplate<ResultCallback<Frame>, Frame> outCallback = docker.attachContainerCmd(containerId)
                                                                                     .withStdOut(true).withStdErr(true)
                                                                                     .withFollowStream(true)
                                                                                     .exec(new ResultCallbackTemplate<ResultCallback<Frame>, Frame>()
                                                                                     {
                                                                                         @Override
                                                                                         public void onNext(Frame object)
                                                                                         {
                                                                                             adlConsoleRecords.add(object.getStreamType(), object.getPayload());
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
                throw new ContainerExecutionException("Interrupted waiting for console output.", e);
            }

            //Start container
            docker.startContainerCmd(containerId).exec();

            WaitContainerResultCallback resultCallback = docker.waitContainerCmd(containerId).start();
            Integer result;
            if (dockerConfiguration.getContainerExecutionTimeout() == null)
                result = resultCallback.awaitStatusCode();
            else
                result = resultCallback.awaitStatusCode(dockerConfiguration.getContainerExecutionTimeout().toMillis(), TimeUnit.MILLISECONDS);

            boolean hasErrors = (result == null || result != 0);

            //Stream console records to logger now
            for (ConsoleRecord adlConsoleRecord : adlConsoleRecords.getRecords())
            {
                for (String adlConsoleRecordLine : adlConsoleRecord.getMessageLines())
                {
                    switch (adlConsoleRecord.getType())
                    {
                        case STDOUT:
                            //adlc puts error messages to stdout, so in case of error send them all to error
                            if (hasErrors)
                                adlLog.error(logToolName, adlConsoleRecordLine);
                            else
                                adlLog.info(logToolName, adlConsoleRecordLine);
                            break;
                        case STDERR:
                            adlLog.error(logToolName, adlConsoleRecordLine);
                            break;
                        //Ignore other types, stdout/stderr is only two we are interested in
                    }
                }
            }

            if (hasErrors)
                throw new ContainerExecutionException(logToolName + " error (" + result + ")");

            //Copy generated files back out of container
            dockerFileMapper.copyFilesFromContainerToHost(containerId);

            //TODO return / log counts
        }
        finally
        {
            try
            {
                //Remove the container now we are done with it
                docker.removeContainerCmd(containerId).withRemoveVolumes(true).exec();
            }
            catch (DockerException e)
            {
                //Log the error but don't fail the build since there might be another exception that occurred beforehand and we
                //don't want to clobber it
                log.error("Error removing Docker container " + containerId + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Generate a unique container name per invocation so that multiple instances can overlap.
     *
     * @return a unique Docker container name for the executor.
     */
    protected String generateDockerContainerName()
    {
        return baseDockerContainerName + "-" + UUID.randomUUID();
    }

    private DistributionSpecifier distributionSpecifierForDockerImage()
    {
        TargetMachine linuxMachine = targetMachineFactory.getLinux().getX86_64(); //Always this machine for Docker
        return new DistributionSpecifier(distributionVersion, new DefaultArchitecture(linuxMachine.getArchitecture().getName()),
                                         new DefaultOperatingSystem(linuxMachine.getOperatingSystemFamily().getName()));
    }

    /**
     * Builds a Docker image for the distribution.
     *
     * @param dockerImageName name of the Docker image to build.
     * @param distributionVersion the version of the distribution to build the image for.
     *
     * @throws DistributionNotFoundException if an ADL distribution with the given version was not found.
     * @throws IOException if some other error occurs.
     */
    private void buildDockerImage(String dockerImageName, String distributionVersion)
    throws DistributionNotFoundException, IOException
    {
        log.info("Building Docker image " + dockerImageName + "...");

        DistributionSpecifier specForDockerImage = distributionSpecifierForDockerImage();
        File adlDistributionArchive = distributionService.resolveDistributionArchive(specForDockerImage);

        //Build a TAR file with the ADL distribution archive and a Dockerfile
        try (ByteArrayOutputStream dOut = new ByteArrayOutputStream();
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(dOut, StandardCharsets.UTF_8.name()))
        {
            //ADL distribution
            //Unzip it on the host here which keeps the Docker build simple
            //otherwise we'd have to apt-get install unzip which involves grabbing apt indexes, etc. which can take a long time for such a simple task
            //Also note that we use ZipFile instead of ZipArchiveInputStream here intentionally
            //- ZAIS does not read the central directory, so unix mode is not read properly
            //TODO what if distribution archive is TAR?
            try (ZipFile adlZip = new ZipFile(adlDistributionArchive))
            {
                for (ZipArchiveEntry adlZipEntry : Collections.list(adlZip.getEntries()))
                {
                    //Entry name for files: tool/<file>
                    //Entry name for dirs: tool/<dir>/
                    String adlTarEntryName = adlZipEntry.getName();
                    if (!adlTarEntryName.startsWith("/"))
                        adlTarEntryName = "/" + adlTarEntryName;
                    adlTarEntryName = "tool" + adlTarEntryName;
                    if (adlZipEntry.isDirectory() && !adlZipEntry.getName().endsWith("/"))
                        adlTarEntryName = adlTarEntryName + "/";

                    TarArchiveEntry adlTarEntry = new TarArchiveEntry(adlTarEntryName);
                    if (!adlTarEntry.isDirectory())
                        adlTarEntry.setSize(adlZipEntry.getSize());
                    if (adlZipEntry.getLastModifiedTime() != null)
                        adlTarEntry.setModTime(adlZipEntry.getLastModifiedTime().toMillis());
                    if (adlZipEntry.getUnixMode() != 0) //for executable flag
                        adlTarEntry.setMode(adlZipEntry.getUnixMode());

                    tarOut.putArchiveEntry(adlTarEntry);
                    try (InputStream adlZipEntryIn = adlZip.getInputStream(adlZipEntry))
                    {
                        IOUtils.copy(adlZipEntryIn, tarOut);
                    }
                    tarOut.closeArchiveEntry();
                }
            }

            //Dockerfile
            String dockerFile = generateDockerfile(specForDockerImage);
            byte[] dockerFileBytes = dockerFile.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry dockerfileEntry = new TarArchiveEntry("Dockerfile");
            dockerfileEntry.setSize(dockerFileBytes.length);
            tarOut.putArchiveEntry(dockerfileEntry);
            tarOut.write(dockerFileBytes);
            tarOut.closeArchiveEntry();

            tarOut.close();

            BuildImageResultCallback callback = docker.buildImageCmd(new ByteArrayInputStream(dOut.toByteArray()))
                                                      .withTags(ImmutableSet.of(dockerImageName))
                                                      .exec(new BuildImageResultCallback()
                                                      {
                                                          @Override
                                                          public void onNext(BuildResponseItem item)
                                                          {
                                                              super.onNext(item);
                                                              if (item.getStream() != null)
                                                              {
                                                                  adlLog.info(TOOL_DOCKER, item.getStream().trim());
                                                              }
                                                          }
                                                      });
            if (dockerConfiguration.getImageBuildTimeout() == null)
                callback.awaitImageId();
            else
                callback.awaitImageId(dockerConfiguration.getImageBuildTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private String generateDockerfile(DistributionSpecifier specForDockerImage)
    throws IOException
    {
        String toolExecutable = executableResolver.resolveExecutable(dockerToolInstallBaseDirectory, specForDockerImage);

        DockerImageDefinition definition = new DockerImageDefinition("ubuntu:20.04");
        definition.getLabels().put(DOCKER_GENERATOR_LABEL, getAdlGradlePluginVersion());
        definition.getCommands().add("COPY /tool/ " + dockerToolInstallBaseDirectory);
        definition.getCommands().add("CMD [\"" + toolExecutable + "\"]");

        dockerImageDefinitionTransformer.transform(definition);

        return definition.toDockerFile();
    }

    /**
     * Checks if a Docker image already exists, attempting to pull it if it doesn't already exist locally.
     *
     * @param imageName the name of the Docker image.
     *
     * @return true if the Docker image exists either already or it was pulled, false if the Docker image does not exist locally or in a known repository.
     *
     * @throws ContainerExecutionException if an error occurs.
     */
    private boolean checkPullDockerImage(String imageName)
    throws ContainerExecutionException
    {
        //Do not use withImageNameFilter() it won't work on later Docker versions
        //and will act like the filter isn't even there (and we delete afterwards!)
        //See: https://github.com/docker-java/docker-java/issues/1523
        //https://github.com/testcontainers/testcontainers-java/pull/3575
        //List<Image> images = docker.listImagesCmd().withImageNameFilter(imageName).exec();

        InspectImageResponse existingImage;
        try
        {
            existingImage = docker.inspectImageCmd(imageName).exec();
        }
        catch (NotFoundException e)
        {
            //Image does not exist, that's fine
            existingImage = null;
        }

        //Forced rebuild - delete any existing local image
        ImageBuildMode dockerImageBuildMode = dockerConfiguration.getImageBuildMode();
        if (dockerImageBuildMode == ImageBuildMode.REBUILD)
        {
            if (existingImage != null && existingImage.getId() != null)
            {
                log.info("Image build mode is " + dockerImageBuildMode + ", removing existing Docker image " + existingImage.getId() + " " + existingImage.getRepoTags());
                docker.removeImageCmd(existingImage.getId()).exec();
            }
            return false; //return false so that image will always be rebuilt locally
        }

        //Rebuild only if previous image is local - and allow re-pull from remotes
        else if (dockerImageBuildMode == ImageBuildMode.DISCARD_LOCAL)
        {
            if (existingImage != null && existingImage.getId() != null && dockerImageIsGeneratedLocally(existingImage))
            {
                log.info("Image build mode is " + dockerImageBuildMode + ", removing existing locally generated Docker image " + existingImage.getId() + " " + existingImage.getRepoTags());
                docker.removeImageCmd(existingImage.getId()).exec();
                existingImage = null;
            }
        }

        if (existingImage == null)
            return pullDockerImage(imageName);
        else
            return true;
    }

    /**
     * Determines whether a Docker image was generated on this system by looking at its labels.  Will return false for images that were downloaded from remote repositories.
     *
     * @param image the image to check.
     *
     * @return true if the Docker image was generated locally, false if not.
     */
    protected boolean dockerImageIsGeneratedLocally(InspectImageResponse image)
    {
        return image.getConfig() != null
                && image.getConfig().getLabels() != null
                && image.getConfig().getLabels().get(DOCKER_GENERATOR_LABEL) != null;
    }

    /**
     * Pulls a Docker image from a remote repository.
     *
     * @param imageName the Docker image name.
     *
     * @return true if the image was pulled successfully, false if the image was not found in the remote repository.
     *
     * @throws ContainerExecutionException if any error part from 'not found' occurs when attempting to pull the Docker image.
     */
    private boolean pullDockerImage(String imageName)
    throws ContainerExecutionException
    {
        adlLog.info(TOOL_DOCKER, "Pulling docker image " + imageName + "...");
        ResultCallback.Adapter<PullResponseItem> pullResponse = docker.pullImageCmd(imageName).exec(new PullImageResultCallback()
        {
            private Throwable firstError;

            @Override
            public void onError(Throwable throwable)
            {
                //Sadly need to replicate a whole lot of code from ResultCallbackTemplate
                //Want to have same logic except that annoying logging
                if (this.firstError == null)
                    this.firstError = throwable;

                try
                {
                    close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }

            protected void throwFirstError()
            {
                if (firstError != null)
                {
                    Throwables.propagateIfPossible(firstError);
                    throw new RuntimeException(firstError);
                }
            }
        });
        try
        {
            if (dockerConfiguration.getImagePullTimeout() == null)
                pullResponse.awaitCompletion();
            else
                pullResponse.awaitCompletion(dockerConfiguration.getImagePullTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            throw new ContainerExecutionException("Interrupted waiting for Docker pull.", e);
        }
        catch (NotFoundException e)
        {
            //Docker image not found remotely
            return false;
        }
        adlLog.info(TOOL_DOCKER, "Docker image downloaded.");
        return true;
    }

    /**
     * @return the version of the currently executing ADL Gradle plugin.
     */
    private String getAdlGradlePluginVersion()
    {
        Package pkg = DockerExecutor.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null)
            return pkg.getImplementationVersion();

        return "unknown";
    }
}
