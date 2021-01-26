package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration common to all ADL code generation configuration, and may be used inside any generation configuration block.
 */
public abstract class GenerationConfiguration
{
    private final DirectoryProperty outputDirectory;
    private final List<String> compilerArgs = new ArrayList<>();

    protected GenerationConfiguration(String generationType)
    {
        outputDirectory = getObjectFactory().directoryProperty()
                            .convention(getProjectLayout().getBuildDirectory().dir("generated/adl/" + generationType));
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @OutputDirectory
    public DirectoryProperty getOutputDirectory()
    {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory.fileValue(outputDirectory);
    }

    @Input
    public List<String> getCompilerArgs()
    {
        return compilerArgs;
    }

    public void setCompilerArgs(List<String> compilerArgs)
    {
        this.compilerArgs.clear();
        this.compilerArgs.addAll(compilerArgs);
    }
}
