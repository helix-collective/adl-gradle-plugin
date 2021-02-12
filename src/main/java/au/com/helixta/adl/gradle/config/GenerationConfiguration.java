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
