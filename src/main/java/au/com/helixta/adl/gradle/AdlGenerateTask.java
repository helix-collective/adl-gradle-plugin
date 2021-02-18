package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.AdlDslMarker;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;
import au.com.helixta.adl.gradle.generator.AdlGenerationException;
import au.com.helixta.adl.gradle.generator.AdlGenerator;
import au.com.helixta.adl.gradle.generator.ColoredAdlToolLogger;
import au.com.helixta.adl.gradle.generator.DockerAdlGenerator;
import au.com.helixta.adl.gradle.generator.NativeAdlGenerator;
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
        /*
        //TODO temp
        AdlExtension adlExtension = getProject().getExtensions().getByType(AdlExtension.class);
        System.out.println("Docker host configued in ADL section: " + adlExtension.getDocker().getHost());

        try
        {
            AdlDistributionService adlDistributionService = new AdlDistributionService(getGradleUserHomeDirProvider(), getFileSystemOperations(), getArchiveOperations(), getProject());
            File adlDir = adlDistributionService.adlDistribution(new AdlDistributionSpec("0.14", "amd64", "linux"));
            System.out.println("ADL dir: " + adlDir);
        }
        catch (AdlDistributionNotFoundException e)
        {
            throw new RuntimeException(e);
        }
         */


        try (AdlGenerator generator = createGenerator())
        {
            for (GenerationConfiguration gen : getGenerations().allGenerations())
            {
                getLogger().warn("Generate: " + gen.getOutputDirectory().get());
                getLogger().warn("   Files: " + this.getSource().getFiles());
                getLogger().warn("   Search dirs: " + this.getSearchDirectories());
            }

            generator.generate(this, getGenerations().allGenerations());
        }
    }

    private AdlGenerator createGenerator()
    {
        //TODO configuration option to fallback to plain output
        StyledTextOutput out = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.INFO);
        StyledTextOutput err = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.ERROR);
        return DockerAdlGenerator.fromConfiguration(getDocker(), new ColoredAdlToolLogger(out, err), getObjectFactory());

        /*
        AdlDistributionService adlDistributionService = new AdlDistributionService(getGradleUserHomeDirProvider(), getFileSystemOperations(), getArchiveOperations(), getProject());
        return new NativeAdlGenerator(getExecOperations(), adlDistributionService, new ColoredAdlToolLogger(out, err));
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
