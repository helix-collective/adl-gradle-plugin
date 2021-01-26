package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.AdlDslMarker;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import au.com.helixta.adl.gradle.generator.AdlGenerationException;
import au.com.helixta.adl.gradle.generator.AdlGenerator;
import au.com.helixta.adl.gradle.generator.ColoredAdlToolLogger;
import au.com.helixta.adl.gradle.generator.DockerAdlGenerator;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AdlDslMarker
public class AdlGenerateTask extends DefaultTask implements AdlConfiguration
{
    private final GenerationsConfiguration generations = getObjectFactory().newInstance(GenerationsConfiguration.class);
    private final DockerConfiguration docker = getObjectFactory().newInstance(DockerConfiguration.class);

    private final ConfigurableFileCollection sourcepath = getObjectFactory().fileCollection();
    private final PatternSet patternSet = getPatternSetFactory().create().include("**/*.adl");
    private final List<File> searchDirectories = new ArrayList<>();
    private boolean verbose;

    @Inject
    protected ObjectFactory getObjectFactory()
    {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory()
    {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getStyledTextOutputFactory()
    {
        throw new UnsupportedOperationException();
    }

    @Nested
    public GenerationsConfiguration getGenerations()
    {
        return generations;
    }

    @Nested
    public DockerConfiguration getDocker()
    {
        return docker;
    }

    public void generations(Action<? super GenerationsConfiguration> configuration)
    {
        configuration.execute(generations);
    }

    public void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(docker);
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @Override
    public FileTree getSourcepath()
    {
        return sourcepath.getAsFileTree().matching(patternSet);
    }

    public void sourcepath(File... sources)
    {
        sourcepath.from((Object[]) sources);
    }

    public void sourcepath(FileCollection... sources)
    {
        sourcepath.from((Object[]) sources);
    }

    public AdlGenerateTask include(String... includes)
    {
        patternSet.include(includes);
        return this;
    }

    public AdlGenerateTask include(Iterable<String> includes)
    {
        patternSet.include(includes);
        return this;
    }

    public AdlGenerateTask exclude(String... excludes)
    {
        patternSet.exclude(excludes);
        return this;
    }

    public AdlGenerateTask exclude(Iterable<String> excludes)
    {
        patternSet.exclude(excludes);
        return this;
    }

    @InputFiles
    @Optional
    @Override
    public List<File> getSearchDirectories()
    {
        return searchDirectories;
    }

    public AdlGenerateTask searchDirectory(File... dirs)
    {
        searchDirectories.addAll(Arrays.asList(dirs));
        return this;
    }

    public AdlGenerateTask searchDirectory(FileCollection... dirs)
    {
        for (FileCollection dir : dirs)
        {
            //For collections, pull out top-level dir and use that
            dir.getAsFileTree().visit(d ->
                                      {
                                          if (d.isDirectory())
                                          {
                                              searchDirectories.add(d.getFile());
                                              d.stopVisiting();
                                          }
                                      });
        }
        return this;
    }

    @Console
    @Override
    public boolean isVerbose()
    {
        return verbose;
    }

    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }

    @TaskAction
    public void generate()
    throws IOException, AdlGenerationException
    {
        try (AdlGenerator generator = createGenerator())
        {
            for (GenerationConfiguration gen : getGenerations().getAllGenerations())
            {
                getLogger().warn("Generate: " + gen.getOutputDirectory().get());
                getLogger().warn("   Files: " + this.getSourcepath().getFiles());
                getLogger().warn("   Search dirs: " + this.getSearchDirectories());
            }

            generator.generate(this, getGenerations().getAllGenerations());
        }
    }

    private AdlGenerator createGenerator()
    {
        //TODO configuration option to fallback to plain output
        StyledTextOutput out = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.INFO);
        StyledTextOutput err = getStyledTextOutputFactory().create(AdlGenerateTask.class, LogLevel.ERROR);
        return DockerAdlGenerator.fromConfiguration(getDocker(), new ColoredAdlToolLogger(out, err), getObjectFactory());
    }
}
