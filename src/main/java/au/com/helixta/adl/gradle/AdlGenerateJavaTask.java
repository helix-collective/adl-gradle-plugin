package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.generator.AdlGenerationException;
import au.com.helixta.adl.gradle.generator.AdlGenerator;
import au.com.helixta.adl.gradle.generator.ColoredAdlToolLogger;
import au.com.helixta.adl.gradle.generator.DockerAdlGenerator;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.inject.Inject;
import java.io.IOException;

public class AdlGenerateJavaTask extends DefaultTask
{
    private final AdlPluginExtension.Generations generations = getObjectFactory().newInstance(AdlPluginExtension.Generations.class);

    @Inject
    protected ObjectFactory getObjectFactory()
    {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getStyledTextOutputFactory()
    {
        throw new UnsupportedOperationException();
    }

    @Nested
    public AdlPluginExtension.Generations getGenerations()
    {
        return generations;
    }

    public void generations(Action<? super AdlPluginExtension.Generations> configuration)
    {
        configuration.execute(generations);
    }

    @TaskAction
    public void generateJava()
    throws IOException, AdlGenerationException
    {
        try (AdlGenerator generator = createGenerator())
        {
            for (AdlPluginExtension.JavaGeneration java : getGenerations().getJava())
            {
                getLogger().warn("Generate java: " + java.getOutputDirectory().get());
                getLogger().warn("   Files: " + java.getSourcepath().getFiles());
                getLogger().warn("   Search dirs: " + java.getSearchDirectories());
            }
            generator.generate(getGenerations().getJava());
        }
    }

    private AdlGenerator createGenerator()
    {
        //TODO configuration option to fallback to plain output
        StyledTextOutput out = getStyledTextOutputFactory().create(AdlGenerateJavaTask.class, LogLevel.INFO);
        StyledTextOutput err = getStyledTextOutputFactory().create(AdlGenerateJavaTask.class, LogLevel.ERROR);
        return DockerAdlGenerator.fromConfiguration(new ColoredAdlToolLogger(out, err), getObjectFactory());
    }
}
