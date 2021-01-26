package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

import java.io.File;

public abstract class TypescriptGenerationConfiguration extends GenerationConfiguration
{
    private boolean generateAdlRuntime;
    private boolean generateTransitive;
    private boolean generateResolver;
    private boolean generateAst = true;
    private final DirectoryProperty runtimeDirectory = getObjectFactory().directoryProperty();

    public TypescriptGenerationConfiguration()
    {
        super("typescript");
    }

    @Input
    public boolean isGenerateAdlRuntime()
    {
        return generateAdlRuntime;
    }

    public void setGenerateAdlRuntime(boolean generateAdlRuntime)
    {
        this.generateAdlRuntime = generateAdlRuntime;
    }

    @Input
    public boolean isGenerateTransitive()
    {
        return generateTransitive;
    }

    public void setGenerateTransitive(boolean generateTransitive)
    {
        this.generateTransitive = generateTransitive;
    }

    @Input
    public boolean isGenerateResolver()
    {
        return generateResolver;
    }

    public void setGenerateResolver(boolean generateResolver)
    {
        this.generateResolver = generateResolver;
    }

    @Input
    public boolean isGenerateAst()
    {
        return generateAst;
    }

    public void setGenerateAst(boolean generateAst)
    {
        this.generateAst = generateAst;
    }

    @Optional
    @OutputDirectory
    public DirectoryProperty getRuntimeDirectory()
    {
        return runtimeDirectory;
    }

    public void setRuntimeDirectory(File runtimeDirectory)
    {
        this.runtimeDirectory.fileValue(runtimeDirectory);
    }
}
