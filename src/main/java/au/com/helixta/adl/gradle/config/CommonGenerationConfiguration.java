package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generation configuration that may be used at a top level of the ADL task configuration.
 */
public abstract class CommonGenerationConfiguration
{
    private final ConfigurableFileCollection sourcepath =
            getObjectFactory().fileCollection();

    private final PatternSet patternSet = getPatternSetFactory().create().include("**/*.adl");

    private final List<File> searchDirectories = new ArrayList<>();

    private boolean verbose;

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract Factory<PatternSet> getPatternSetFactory();

    //TODO maybe make this non-abstract - too easy to forget to implement
    protected abstract String generationType();

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
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

    public CommonGenerationConfiguration include(String... includes)
    {
        patternSet.include(includes);
        return this;
    }

    public CommonGenerationConfiguration include(Iterable<String> includes)
    {
        patternSet.include(includes);
        return this;
    }

    public CommonGenerationConfiguration exclude(String... excludes)
    {
        patternSet.exclude(excludes);
        return this;
    }

    public CommonGenerationConfiguration exclude(Iterable<String> excludes)
    {
        patternSet.exclude(excludes);
        return this;
    }

    @InputFiles
    @Optional
    public List<File> getSearchDirectories()
    {
        return searchDirectories;
    }

    public CommonGenerationConfiguration searchDirectory(File... dirs)
    {
        searchDirectories.addAll(Arrays.asList(dirs));
        return this;
    }

    public CommonGenerationConfiguration searchDirectory(FileCollection... dirs)
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
    public boolean isVerbose()
    {
        return verbose;
    }

    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }
}
