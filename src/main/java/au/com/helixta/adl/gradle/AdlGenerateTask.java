package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.AdlDslMarker;
import au.com.helixta.adl.gradle.config.AdlPlatform;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import au.com.helixta.adl.gradle.containerexecutor.ContainerTool;
import au.com.helixta.adl.gradle.containerexecutor.DockerClientFactory;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;
import au.com.helixta.adl.gradle.generator.AdlGenerationException;
import au.com.helixta.adl.gradle.generator.AdlGenerator;
import au.com.helixta.adl.gradle.generator.AdlToolGenerator;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import au.com.helixta.adl.gradle.generator.ColoredAdlToolLogger;
import au.com.helixta.adl.gradle.generator.DockerAdlGenerator;
import au.com.helixta.adl.gradle.generator.NativeAdlGenerator;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.sun.jna.Platform;
import org.gradle.api.Action;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;

@AdlDslMarker
public abstract class AdlGenerateTask extends SourceTask implements AdlConfiguration, AdlExtension
{
    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract StyledTextOutputFactory getStyledTextOutputFactory();

    @Inject
    protected abstract GradleUserHomeDirProvider getGradleUserHomeDirProvider();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract TargetMachineFactory getTargetMachineFactory();

    private GenerationsConfiguration generations = getObjectFactory().newInstance(GenerationsConfiguration.class);
    private DockerConfiguration docker = getObjectFactory().newInstance(DockerConfiguration.class);

    public AdlGenerateTask()
    {
        //TODO does a default pattern belong here or in the source set definition?
        include("**/*.adl");
    }

    @TaskAction
    public void generate()
    throws IOException, AdlGenerationException
    {
        try (AdlGenerator generator = createGenerator())
        {
            for (GenerationConfiguration gen : getGenerations().allGenerations())
            {
                getLogger().warn("Generate: " + gen.getOutputDirectory().get());
                getLogger().warn("   Files: " + this.getSource().getFiles());
                getLogger().warn("   Search dirs: " + this.getSearchDirectories().getFiles());
            }

            generator.generate(this, getGenerations().allGenerations());
        }
    }

    /*
    private DockerClient createDockerClient()
    {
        DockerClientConfig config = dockerClientConfig(dockerConfiguration);

        //For Windows, don't even try using a unix: socket
        if (config.getDockerHost() != null && "unix".equals(config.getDockerHost().getScheme()) && Platform.isWindows())
            throw new RuntimeException("Docker on Windows platform has not been configured. The ADL code generator requires Docker for running on this platform. Install Docker and configure environment variables such as DOCKER_HOST appropriately.");

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        DockerClient docker = DockerClientImpl.getInstance(config, httpClient);

        //Test that Docker is working on this platform
        try
        {
            docker.pingCmd().exec();
        }
        catch (RuntimeException e)
        {
            //Unfortunately exceptions are wrapped in runtime exceptions, just treat any runtime exception as failure
            throw new RuntimeException("Docker is not functioning properly - ADL generation failed. Check Docker configuration. " + e.getMessage(), e);
        }

        //If we get here Docker ping worked
        return new DockerAdlGenerator(docker, dockerConfiguration, adlLog, adlDistributionService, targetMachineFactory, objectFactory, archiveOperations);
    }
     */

    private ContainerTool.ExecutionPlatform executionPlatform()
    {
        AdlPlatform platform = getPlatform();
        if (platform == null)
            platform = AdlPlatform.AUTO;

        switch (platform)
        {
            case AUTO:
                return ContainerTool.ExecutionPlatform.AUTO;
            case DOCKER:
                return ContainerTool.ExecutionPlatform.DOCKER;
            case NATIVE:
                return ContainerTool.ExecutionPlatform.NATIVE;
            default:
                throw new Error("Unknown platform: " + platform);
        }
    }

    private AdlGenerator createGenerator()
    throws IOException
    {
        StyledTextOutput out = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.INFO);
        StyledTextOutput err = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.ERROR);
        ColoredAdlToolLogger adlLogger = new ColoredAdlToolLogger(out, err, getProject().getLogger().isEnabled(LogLevel.INFO));

        //AdlDistributionService adlDistributionService = new AdlDistributionService(getGradleUserHomeDirProvider(), getFileSystemOperations(), getArchiveOperations(), getProject());
        DockerClientFactory dockerFactory = new DockerClientFactory(docker);

        ContainerTool.Environment environment = new ContainerTool.Environment(getExecOperations(), adlLogger, dockerFactory, getTargetMachineFactory(), getObjectFactory(), getArchiveOperations(), new ArchiveProcessor(getArchiveOperations()), getGradleUserHomeDirProvider(), getFileSystemOperations(), getProject(), getLogger());

        return new AdlToolGenerator(environment, docker, executionPlatform());

        /*
        NativeAdlGenerator nativeAdlGenerator = new NativeAdlGenerator(getExecOperations(), getArchiveOperations(), adlDistributionService, adlLogger);

        AdlPlatform platform = getPlatform();
        if (platform == null)
            platform = AdlPlatform.AUTO;

        if (platform == AdlPlatform.AUTO)
        {
            try
            {
                nativeAdlGenerator.resolveAdlDistribution(this);

                //Successfully resolved, use native
                platform = AdlPlatform.NATIVE;
            }
            catch (DistributionNotFoundException e)
            {
                //Not found - fallback to docker
                platform = AdlPlatform.DOCKER;
            }
        }
        getLogger().info("Selected ADL platform: " + platform);

        switch (platform)
        {
            case DOCKER:
                //TODO configuration option to fallback to plain output
                return DockerAdlGenerator.fromConfiguration(getDocker(), adlLogger, adlDistributionService, getTargetMachineFactory(), getObjectFactory(), getArchiveOperations());
            case NATIVE:
                return nativeAdlGenerator;
            default:
                throw new RuntimeException("Unknown ADL platform selected: " + platform);
        }
         */
    }

    @Override
    public GenerationsConfiguration getGenerations()
    {
        return generations;
    }

    @Override
    public DockerConfiguration getDocker()
    {
        return docker;
    }

    public void setGenerations(GenerationsConfiguration generations)
    {
        this.generations = generations;
    }

    public void setDocker(DockerConfiguration docker)
    {
        this.docker = docker;
    }

    @Override
    public void generations(Action<? super GenerationsConfiguration> configuration)
    {
        configuration.execute(generations);
    }

    @Override
    public void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(docker);
    }
}
