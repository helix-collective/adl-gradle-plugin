package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

public abstract class TypescriptGenerationConfiguration extends GenerationConfiguration implements ManifestGenerationSupport
{
    private boolean generateAdlRuntime;
    private boolean generateTransitive;
    private boolean generateResolver;
    private boolean generateAst = true;
    private String runtimeModuleName = "runtime";

    private final RegularFileProperty manifest = getObjectFactory().fileProperty();

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
    @Input
    public String getRuntimeModuleName()
    {
        return runtimeModuleName;
    }

    public void setRuntimeModuleName(String runtimeModuleName)
    {
        this.runtimeModuleName = runtimeModuleName;
    }

    @Override
    @OutputFile
    @Optional
    public RegularFileProperty getManifest()
    {
        return manifest;
    }

    public void setManifest(File manifestFile)
    {
        manifest.fileValue(manifestFile);
    }

    public TypescriptGenerationConfiguration copyFrom(TypescriptGenerationConfiguration other)
    {
        super.baseCopyFrom(other);
        setGenerateAdlRuntime(other.isGenerateAdlRuntime());
        setGenerateTransitive(other.isGenerateTransitive());
        setGenerateResolver(other.isGenerateResolver());
        setGenerateAst(other.isGenerateAst());
        setRuntimeModuleName(other.getRuntimeModuleName());
        setManifest(other.getManifest().getAsFile().getOrNull());
        return this;
    }
}
