package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration common to all ADL code generation configuration, and may be used inside any generation configuration block.
 */
@AdlDslMarker
public abstract class GenerationConfiguration
{
    private final DirectoryProperty outputDirectory;
    private final List<String> compilerArgs = new ArrayList<>();
    private final String generationType;

    protected GenerationConfiguration(String generationType)
    {
        this.generationType = generationType;
        outputDirectory = getObjectFactory().directoryProperty();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * @return the directory where generated code is written.
     */
    @OutputDirectory
    public DirectoryProperty getOutputDirectory()
    {
        return outputDirectory;
    }

    /**
     * Sets the directory where generated code is written.
     */
    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory.fileValue(outputDirectory);
    }

    /**
     * @return a list of extra arguments passed directly to the ADL compiler tool.  Can be used to pass arguments not directly
     * supported by the ADL plugin.
     */
    @Input
    public List<String> getCompilerArgs()
    {
        return compilerArgs;
    }

    /**
     * Sets extra arguments that are passed directly to the ADL compiler tool.
     */
    public void setCompilerArgs(List<String> compilerArgs)
    {
        this.compilerArgs.clear();
        this.compilerArgs.addAll(compilerArgs);
    }

    /**
     * @return the name of the generation type that reflects the type of source code generated.  Read-only.
     */
    public String generationType()
    {
        return generationType;
    }

    protected void baseCopyFrom(GenerationConfiguration other)
    {
        outputDirectory.set(other.outputDirectory);
        compilerArgs.addAll(other.compilerArgs);
    }
}
