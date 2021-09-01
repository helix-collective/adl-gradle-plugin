package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.DistributionService;
import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;
import au.com.helixta.adl.gradle.generator.AdlToolLogger;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import com.github.dockerjava.api.DockerClient;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public abstract class ContainerTool<C>
{
    private final StaticToolConfiguration staticToolConfiguration;
    private final Environment environment;

    protected ContainerTool(StaticToolConfiguration staticToolConfiguration, Environment environment)
    {
        this.staticToolConfiguration = Objects.requireNonNull(staticToolConfiguration);
        this.environment = Objects.requireNonNull(environment);
    }

    private DistributionSpecifier nativeDistributionSpecifier(String distributionVersion)
    {
        NativePlatform host = DefaultNativePlatform.host();
        return new DistributionSpecifier(distributionVersion, host.getArchitecture(), host.getOperatingSystem());
    }

    public void executeNative(C config)
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        Objects.requireNonNull(config);

        DistributionSpecifier distributionSpecifier = nativeDistributionSpecifier(readDistributionVersion(config));

        NativeExecutor nativeExecutor = new NativeExecutor(staticToolConfiguration.distributionService, distributionSpecifier,
                                                           staticToolConfiguration.executableResolver, environment.execOperations,
                                                           environment.archiveProcessor, environment.toolLogger,
                                                           staticToolConfiguration.logToolName);
        PreparedCommandLine commandLine = createCommandLine(config);
        nativeExecutor.execute(commandLine);
    }

    public void executeDocker(C config)
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        Objects.requireNonNull(config);

        try (DockerClient dockerClient = environment.dockerClientFactory.createDockerClient())
        {
            DockerExecutor dockerExecutor = new DockerExecutor(dockerClient, staticToolConfiguration.distributionService, staticToolConfiguration.executableResolver,
                                                               staticToolConfiguration.dockerCommandLinePostProcessor,
                                                               staticToolConfiguration.dockerToolInstallBaseDirectory, staticToolConfiguration.dockerMappedBaseDirectory,
                                                               staticToolConfiguration.dockerImageDefinitionTransformer,
                                                               readDistributionVersion(config), staticToolConfiguration.baseDockerImageName,
                                                               staticToolConfiguration.baseDockerContainerName, readDockerConfiguration(config), environment.toolLogger,
                                                               staticToolConfiguration.logToolName, environment.targetMachineFactory, environment.objectFactory,
                                                               environment.archiveProcessor);
            PreparedCommandLine commandLine = createCommandLine(config);
            dockerExecutor.execute(commandLine);
        }
    }

    public void execute(C config, ExecutionPlatform platform)
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        if (platform == null)
            platform = ExecutionPlatform.AUTO;

        if (platform == ExecutionPlatform.AUTO)
        {
            try
            {
                //Attempt to use native if it exists - check if there exists a resolvable distribution for the native platform
                DistributionSpecifier distributionSpecifier = nativeDistributionSpecifier(readDistributionVersion(config));
                staticToolConfiguration.distributionService.resolveDistribution(distributionSpecifier);

                //Successfully resolved, use native
                platform = ExecutionPlatform.NATIVE;
            }
            catch (DistributionNotFoundException e)
            {
                //Not found - fallback to docker
                platform = ExecutionPlatform.DOCKER;
            }
        }
        environment.gradleLogger.info("Selected tool platform: " + platform);

        switch (platform)
        {
            case DOCKER:
                executeDocker(config);
                return;
            case NATIVE:
                executeNative(config);
                return;
            default: //Should not happen since all enum options covered
                throw new Error("Unknown platform selected: " + platform);
        }
    }

    protected abstract PreparedCommandLine createCommandLine(C config);
    protected abstract String readDistributionVersion(C config);
    protected abstract DockerConfiguration readDockerConfiguration(C config);

    public static class Environment
    {
        private final ExecOperations execOperations;
        private final AdlToolLogger toolLogger;
        private final DockerClientFactory dockerClientFactory;
        private final TargetMachineFactory targetMachineFactory;
        private final ObjectFactory objectFactory;
        private final ArchiveOperations archiveOperations;
        private final ArchiveProcessor archiveProcessor;
        private final GradleUserHomeDirProvider homeDirProvider;
        private final FileSystemOperations fileSystemOperations;
        private final Project project;
        private final Logger gradleLogger;

        public Environment(ExecOperations execOperations, AdlToolLogger toolLogger, DockerClientFactory dockerClientFactory, TargetMachineFactory targetMachineFactory,
                           ObjectFactory objectFactory, ArchiveOperations archiveOperations, ArchiveProcessor archiveProcessor,
                           GradleUserHomeDirProvider homeDirProvider, FileSystemOperations fileSystemOperations, Project project,
                           Logger gradleLogger)
        {
            this.execOperations = Objects.requireNonNull(execOperations);
            this.toolLogger = Objects.requireNonNull(toolLogger);
            this.dockerClientFactory = Objects.requireNonNull(dockerClientFactory);
            this.targetMachineFactory = Objects.requireNonNull(targetMachineFactory);
            this.objectFactory = Objects.requireNonNull(objectFactory);
            this.archiveOperations = Objects.requireNonNull(archiveOperations);
            this.archiveProcessor = Objects.requireNonNull(archiveProcessor);
            this.homeDirProvider = Objects.requireNonNull(homeDirProvider);
            this.fileSystemOperations = Objects.requireNonNull(fileSystemOperations);
            this.project = Objects.requireNonNull(project);
            this.gradleLogger = Objects.requireNonNull(gradleLogger);
        }

        public ExecOperations getExecOperations()
        {
            return execOperations;
        }

        public AdlToolLogger getToolLogger()
        {
            return toolLogger;
        }

        public DockerClientFactory getDockerClientFactory()
        {
            return dockerClientFactory;
        }

        public TargetMachineFactory getTargetMachineFactory()
        {
            return targetMachineFactory;
        }

        public ObjectFactory getObjectFactory()
        {
            return objectFactory;
        }

        public ArchiveOperations getArchiveOperations()
        {
            return archiveOperations;
        }

        public ArchiveProcessor getArchiveProcessor()
        {
            return archiveProcessor;
        }

        public GradleUserHomeDirProvider getHomeDirProvider()
        {
            return homeDirProvider;
        }

        public FileSystemOperations getFileSystemOperations()
        {
            return fileSystemOperations;
        }

        public Project getProject()
        {
            return project;
        }

        public Logger getGradleLogger()
        {
            return gradleLogger;
        }
    }

    protected static class StaticToolConfiguration
    {
        private final DistributionService distributionService;
        private final ExecutableResolver executableResolver;
        private final String logToolName;
        private final String dockerToolInstallBaseDirectory;
        private final String dockerMappedBaseDirectory;
        private final DockerImageDefinitionTransformer dockerImageDefinitionTransformer;
        private final String baseDockerImageName;
        private final String baseDockerContainerName;
        private final UnaryOperator<List<String>> dockerCommandLinePostProcessor;

        public StaticToolConfiguration(DistributionService distributionService, ExecutableResolver executableResolver, String logToolName, String dockerToolInstallBaseDirectory,
                                       String dockerMappedBaseDirectory, DockerImageDefinitionTransformer dockerImageDefinitionTransformer,
                                       String baseDockerImageName, String baseDockerContainerName,
                                       UnaryOperator<List<String>> dockerCommandLinePostProcessor)
        {
            this.distributionService = Objects.requireNonNull(distributionService);
            this.executableResolver = Objects.requireNonNull(executableResolver);
            this.logToolName = Objects.requireNonNull(logToolName);
            this.dockerToolInstallBaseDirectory = Objects.requireNonNull(dockerToolInstallBaseDirectory);
            this.dockerMappedBaseDirectory = Objects.requireNonNull(dockerMappedBaseDirectory);
            this.dockerImageDefinitionTransformer = Objects.requireNonNull(dockerImageDefinitionTransformer);
            this.baseDockerImageName = Objects.requireNonNull(baseDockerImageName);
            this.baseDockerContainerName = Objects.requireNonNull(baseDockerContainerName);
            this.dockerCommandLinePostProcessor = Objects.requireNonNull(dockerCommandLinePostProcessor);
        }
    }
}
