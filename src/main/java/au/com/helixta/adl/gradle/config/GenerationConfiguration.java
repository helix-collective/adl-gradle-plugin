package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration common to all ADL code generation configuration, and may be used inside any generation configuration block.
 */
public abstract class GenerationConfiguration extends CommonGenerationConfiguration
{
    private final DirectoryProperty outputDirectory =
            getObjectFactory().directoryProperty()
                              .convention(getProjectLayout().getBuildDirectory().dir("generated/adl/" + generationType()));

    private final List<String> compilerArgs = new ArrayList<>();

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
