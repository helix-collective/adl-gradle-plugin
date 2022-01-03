package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.AdlDslMarker;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import au.com.helixta.adl.gradle.containerexecutor.ContainerTool;
import au.com.helixta.adl.gradle.containerexecutor.DockerClientFactory;
import au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform;
import au.com.helixta.adl.gradle.generator.AdlGenerationException;
import au.com.helixta.adl.gradle.generator.AdlGenerator;
import au.com.helixta.adl.gradle.generator.AdlToolGenerator;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import au.com.helixta.adl.gradle.generator.ColoredAdlToolLogger;
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
                getLogger().debug("Generate: " + gen.getOutputDirectory().get());
                getLogger().debug("   Files: " + this.getSource().getFiles());
                getLogger().debug("   Search dirs: " + this.getSearchDirectories().getFiles());
            }

            generator.generate(this, getGenerations().allGenerations());
        }
    }

    private AdlGenerator createGenerator()
    throws IOException
    {
        StyledTextOutput out = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.INFO);
        StyledTextOutput err = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.ERROR);
        ColoredAdlToolLogger adlLogger = new ColoredAdlToolLogger(out, err, getProject().getLogger().isEnabled(LogLevel.INFO));

        DockerClientFactory dockerFactory = new DockerClientFactory(docker);

        ContainerTool.Environment environment = new ContainerTool.Environment(getExecOperations(), adlLogger, dockerFactory, getTargetMachineFactory(), getObjectFactory(), getArchiveOperations(), new ArchiveProcessor(getArchiveOperations()), getGradleUserHomeDirProvider(), getFileSystemOperations(), getProject(), getLogger());

        ExecutionPlatform platform = getPlatform();
        if (platform == null)
            platform = ExecutionPlatform.AUTO;

        return new AdlToolGenerator(environment, docker, platform);
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
