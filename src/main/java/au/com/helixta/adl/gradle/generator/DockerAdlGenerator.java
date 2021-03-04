package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.ManifestGenerationSupport;
import au.com.helixta.adl.gradle.distribution.AdlDistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;
import au.com.helixta.adl.gradle.distribution.AdlDistributionSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BuildResponseItem;
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
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultArchitecture;
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DockerAdlGenerator implements AdlGenerator
{
    private static final Logger log = Logging.getLogger(DockerAdlGenerator.class);

    private static final String TOOL_ADLC = "adlc";
    private static final String TOOL_DOCKER = "docker";

    private final AdlToolLogger adlLog;
    private final AdlDistributionService adlDistributionService;
    private final DockerClient docker;
    private final TargetMachineFactory targetMachineFactory;
    private final ObjectFactory objectFactory;
    private final ArchiveOperations archiveOperations;

    private final AdlcCommandLineGenerator adlcCommandProcessor = new AdlcCommandLineGenerator();

    public DockerAdlGenerator(DockerClient docker, AdlToolLogger adlLog, AdlDistributionService adlDistributionService,
                              TargetMachineFactory targetMachineFactory, ObjectFactory objectFactory,
                              ArchiveOperations archiveOperations)
    {
        this.docker = Objects.requireNonNull(docker);
        this.adlLog = Objects.requireNonNull(adlLog);
        this.adlDistributionService = Objects.requireNonNull(adlDistributionService);
        this.targetMachineFactory = Objects.requireNonNull(targetMachineFactory);
        this.objectFactory = Objects.requireNonNull(objectFactory);
        this.archiveOperations = Objects.requireNonNull(archiveOperations);
    }

    public static DockerAdlGenerator fromConfiguration(DockerConfiguration dockerConfiguration,
                                                       AdlToolLogger adlLog,
                                                       AdlDistributionService adlDistributionService,
                                                       TargetMachineFactory targetMachineFactory,
                                                       ObjectFactory objectFactory,
                                                       ArchiveOperations archiveOperations)
    {
        DockerClientConfig config = dockerClientConfig(dockerConfiguration);
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                                            .dockerHost(config.getDockerHost())
                                            .sslConfig(config.getSSLConfig())
                                            .build();

         DockerClient docker = DockerClientImpl.getInstance(config, httpClient);
         return new DockerAdlGenerator(docker, adlLog, adlDistributionService, targetMachineFactory, objectFactory, archiveOperations);
    }

    private static DockerClientConfig dockerClientConfig(DockerConfiguration config)
    {
        //By default, everything configured from environment, such as environment variables
        DefaultDockerClientConfig.Builder c = DefaultDockerClientConfig.createDefaultConfigBuilder();

        //Augment with anything overridden from the docker configuration
        if (config.getHost() != null)
            c.withDockerHost(config.getHost().toString());
        if (config.getTlsVerify() != null)
            c.withDockerTlsVerify(config.getTlsVerify());
        if (config.getCertPath().isPresent())
            c.withDockerCertPath(config.getCertPath().getAsFile().get().getAbsolutePath());
        if (config.getApiVersion() != null)
            c.withApiVersion(config.getApiVersion());
        if (config.getRegistryUrl() != null)
            c.withRegistryUrl(config.getRegistryUrl().toString());
        if (config.getRegistryUsername() != null)
            c.withRegistryUsername(config.getRegistryUsername());
        if (config.getRegistryPassword() != null)
            c.withRegistryPassword(config.getRegistryPassword());

        return c.build();
    }

    private boolean pullDockerImage(String imageName)
    throws AdlGenerationException
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
            pullResponse.awaitCompletion(); //TODO timeouts
        }
        catch (InterruptedException e)
        {
            throw new AdlGenerationException("Interrupted waiting for Docker pull.", e);
        }
        catch (NotFoundException e)
        {
            //Docker image not found remotely
            return false;
        }
        adlLog.info(TOOL_DOCKER, "Docker image downloaded.");
        return true;
    }

    private boolean checkPullDockerImage(String imageName)
    throws AdlGenerationException
    {
        List<Image> images = docker.listImagesCmd().withImageNameFilter(imageName).exec();
        if (images.isEmpty())
            return pullDockerImage(imageName);
        else
            return true;
    }

    protected String containerName()
    {
        return "adl";
    }

    protected String imageName(String adlVersion)
    {
        return "adl/adlc:" + adlVersion;
    }

    protected String getSourcePathInContainer()
    {
        return "/data/sources/";
    }

    protected String getOutputPathInContainer()
    {
        return "/data/generated/";
    }

    protected String getManifestOutputPathInContainer()
    {
        return "/data/";
    }

    protected String getSearchDirectoryPathInContainer()
    {
        return "/data/searchdirs/";
    }

    private void buildDockerImage(String adlVersion)
    throws AdlDistributionNotFoundException, IOException
    {
        log.info("Building Docker image for ADL " + adlVersion + "...");

        TargetMachine linuxMachine = targetMachineFactory.getLinux().getX86_64();
        AdlDistributionSpec specForDockerImage = new AdlDistributionSpec(adlVersion, new DefaultArchitecture(linuxMachine.getArchitecture().getName()),
                                                                                                             new DefaultOperatingSystem(linuxMachine.getOperatingSystemFamily().getName()));
        File adlDistributionArchive = adlDistributionService.resolveAdlDistributionArchive(specForDockerImage);

        //Build a TAR file with the ADL distribution archive and a Dockerfile
        try (ByteArrayOutputStream dOut = new ByteArrayOutputStream();
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(dOut, StandardCharsets.UTF_8.name()))
        {
            //ADL distribution
            //Unzip it on the host here which keeps the Docker build simple
            //otherwise we'd have to apt-get install unzip which involves grabbing apt indexes, etc. which can take a long time for such a simple task
            //Also note that we use ZipFile instead of ZipArchiveInputStream here intentionally
            //- ZAIS does not read the central directory, so unix mode is not read properly
            try (ZipFile adlZip = new ZipFile(adlDistributionArchive))
            {
                for (ZipArchiveEntry adlZipEntry : Collections.list(adlZip.getEntries()))
                {
                    //Entry name for files: adl/<file>
                    //Entry name for dirs: adl/<dir>/
                    String adlTarEntryName = adlZipEntry.getName();
                    if (!adlTarEntryName.startsWith("/"))
                        adlTarEntryName = "/" + adlTarEntryName;
                    adlTarEntryName = "adl" + adlTarEntryName;
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
            byte[] dockerFileBytes =
                ("FROM ubuntu:20.04\n" +
                 "COPY /adl/ /opt/adl/\n" +
                 "CMD [\"/opt/adl/bin/adlc\"]"
                ).getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry dockerfileEntry = new TarArchiveEntry("Dockerfile");
            dockerfileEntry.setSize(dockerFileBytes.length);
            tarOut.putArchiveEntry(dockerfileEntry);
            tarOut.write(dockerFileBytes);
            tarOut.closeArchiveEntry();

            tarOut.close();

            docker.buildImageCmd(new ByteArrayInputStream(dOut.toByteArray()))
                  .withTags(ImmutableSet.of(imageName(adlVersion)))
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
                  })
                  .awaitImageId(); //TODO timeouts
        }
    }

    private void runAdlc(AdlConfiguration adlConfiguration, GenerationConfiguration generation)
    throws AdlGenerationException
    {
        DockerFileSystemMapper dockerFileSystemMapper = new DockerFileSystemMapper(objectFactory, archiveOperations, docker);

        //Generate archive for source files
        dockerFileSystemMapper.addInputFiles(new FileSystemMapper.LabelledFileTree(AdlFileTreeLabel.SOURCES, adlConfiguration.getSource()),
                                             getSourcePathInContainer());

        //and searchdirs
        int searchDirIndex = 0;
        for (File searchDirectory : adlConfiguration.getSearchDirectories())
        {
            String searchDirContainerPath = getSearchDirectoryPathInContainer() + searchDirIndex + "/";
            dockerFileSystemMapper.addInputFiles(searchDirectory, searchDirContainerPath);
            searchDirIndex++;
        }

        //Register output files
        dockerFileSystemMapper.registerOutputDirectory(generation.getOutputDirectory().get(), getOutputPathInContainer());
        if (generation instanceof ManifestGenerationSupport)
        {
            ManifestGenerationSupport manifestGeneration = (ManifestGenerationSupport)generation;
            if (manifestGeneration.getManifest().isPresent())
            {
                File manifestFileOnHost = manifestGeneration.getManifest().get().getAsFile();
                String manifestContainerFile = getManifestOutputPathInContainer() + manifestFileOnHost.getName();
                dockerFileSystemMapper.registerOutputFile(manifestGeneration.getManifest().get(), manifestContainerFile);
            }
        }

        //Put together the ADL command
        List<String> adlcCommand = new ArrayList<>();
        adlcCommand.add("/opt/adl/bin/adlc");
        adlcCommand.addAll(adlcCommandProcessor.createAdlcCommand(adlConfiguration, generation, dockerFileSystemMapper));
        log.info("adlc command " + adlcCommand);

        //Create Docker container that can execute ADL compiler
        String containerName = containerName();
        CreateContainerResponse c = docker.createContainerCmd(imageName(adlConfiguration.getVersion()))
                                          .withHostConfig(HostConfig.newHostConfig().withAutoRemove(false))
                                          .withName(containerName)
                                          .withCmd(adlcCommand)
                                          .exec();
        String containerId = c.getId();


        try
        {
            //Copy source files to the container
            dockerFileSystemMapper.copyFilesToContainer(containerId);

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
                throw new AdlGenerationException("Interrupted waiting for console output.", e);
            }

            docker.startContainerCmd(containerId).exec();

            Integer result = docker.waitContainerCmd(containerId).start().awaitStatusCode(); //TODO timeout
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
                                adlLog.error(TOOL_ADLC, adlConsoleRecordLine);
                            else
                                adlLog.info(TOOL_ADLC, adlConsoleRecordLine);
                            break;
                        case STDERR:
                            adlLog.error(TOOL_ADLC, adlConsoleRecordLine);
                            break;
                        //Ignore other types, stdout/stderr is only two we are interested in
                    }
                }
            }

            if (hasErrors)
                throw new AdlGenerationException("adlc error (" + result + ")");

            //Copy generated files back out of container
            Map<File, Integer> copyCounts = new HashMap<>(dockerFileSystemMapper.copyFilesFromContainer(containerId));

            //Log the files that were generated
            if (generation instanceof ManifestGenerationSupport)
            {
                ManifestGenerationSupport manifestGeneration = (ManifestGenerationSupport)generation;
                if (manifestGeneration.getManifest().isPresent())
                {
                    Integer generatedManifestFileCount = copyCounts.remove(manifestGeneration.getManifest().get().getAsFile());
                    if (generatedManifestFileCount == null)
                        generatedManifestFileCount = 0;
                    log.info(generatedManifestFileCount + " manifest file(s) generated.");
                }
            }
            int generatedOtherFileCount = copyCounts.values().stream().mapToInt(Integer::valueOf).sum();
            log.info(generatedOtherFileCount + " " + generation.generationType() + " source file(s) generated.");
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

    @Override
    public void generate(AdlConfiguration configuration, Iterable<? extends GenerationConfiguration> generations)
    throws AdlGenerationException
    {
        //Check if the image exists, if not then try to pull it
        boolean imageAvailable = checkPullDockerImage(imageName(configuration.getVersion()));
        if (!imageAvailable)
        {
            log.info("ADL Docker image not found in repository so it will be built.");
            try
            {
                buildDockerImage(configuration.getVersion());
            }
            catch (AdlDistributionNotFoundException e)
            {
                throw new AdlGenerationException("No Docker image or ADL distribution found for ADL version " + configuration.getVersion(), e);
            }
            catch (IOException e)
            {
                throw new AdlGenerationException(e.getMessage(), e);
            }
        }

        for (GenerationConfiguration generation : generations)
        {
            runAdlc(configuration, generation);
        }
    }

    @Override
    public void close() throws IOException
    {
        docker.close();
    }

    private static class ConsoleRecorder
    {
        private final List<ConsoleRecord> records = new ArrayList<>();

        public synchronized void add(StreamType type, byte[] messageBytes)
        {
            //Append to existing last record if the same type
            if (!records.isEmpty())
            {
                ConsoleRecord lastRecord = records.get(records.size() - 1);
                if (lastRecord.getType() == type)
                {
                    lastRecord.append(messageBytes);
                    return;
                }
            }

            //Otherwise new record
            records.add(new ConsoleRecord(type, messageBytes));
        }

        public synchronized List<? extends ConsoleRecord> getRecords()
        {
            return new ArrayList<>(records);
        }
    }

    private static class ConsoleRecord
    {
        private final StreamType type;
        private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

        //Keeping everything as bytes for as long as possible in case a multi-byte character is split across boundaries
        public ConsoleRecord(StreamType type, byte[] messageBytes)
        {
            this.type = type;
            append(messageBytes);
        }

        public StreamType getType()
        {
            return type;
        }

        public String getMessage()
        {
            try
            {
                return messageBuffer.toString(StandardCharsets.UTF_8.name());
            }
            catch (UnsupportedEncodingException e)
            {
                //UTF-8 should always be supported
                throw new IOError(e);
            }
        }

        public List<String> getMessageLines()
        {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(messageBuffer.toByteArray()))))
            {
                return br.lines().collect(Collectors.toList());
            }
            catch (IOException e)
            {
                //Shouldn't happen for in-memory - and UTF-8 encoding errors will decode to bad chars not throw exception
                throw new IOError(e);
            }
        }

        public void append(byte[] messageBytes)
        {
            try
            {
                messageBuffer.write(messageBytes);
            }
            catch (IOException e)
            {
                //In-memory, should not happen
                throw new IOError(e);
            }
        }
    }
}
