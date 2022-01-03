package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

/**
 * Typescript code generation configuration for ADL.
 */
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

    /**
     * @return whether the ADL core runtime code is generated.
     */
    @Input
    public boolean isGenerateAdlRuntime()
    {
        return generateAdlRuntime;
    }

    /**
     * Sets whether the ADL core runtime code is generated.
     */
    public void setGenerateAdlRuntime(boolean generateAdlRuntime)
    {
        this.generateAdlRuntime = generateAdlRuntime;
    }

    /**
     * @return whether code is also generated for the transitive dependencies (from search directories) of the ADL files.
     */
    @Input
    public boolean isGenerateTransitive()
    {
        return generateTransitive;
    }

    /**
     * Sets whether code is also generated for the transitive dependencies (from search directories) of the ADL files.
     */
    public void setGenerateTransitive(boolean generateTransitive)
    {
        this.generateTransitive = generateTransitive;
    }

    /**
     * @return whether the resolver map for all generated adl files is generated.
     */
    @Input
    public boolean isGenerateResolver()
    {
        return generateResolver;
    }

    /**
     * Sets whether the resolver map for all generated adl files is generated.
     */
    public void setGenerateResolver(boolean generateResolver)
    {
        this.generateResolver = generateResolver;
    }

    /**
     * @return if the ASTs are generated.
     */
    @Input
    public boolean isGenerateAst()
    {
        return generateAst;
    }

    /**
     * Sets whether the ASTs are generated.
     */
    public void setGenerateAst(boolean generateAst)
    {
        this.generateAst = generateAst;
    }

    /**
     * @return the name of the directory where runtime code is written.
     */
    @Optional
    @Input
    public String getRuntimeModuleName()
    {
        return runtimeModuleName;
    }

    /**
     * Sets the name of the directory where the runtime code is written.
     */
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

    /**
     * If specified, write a manifest file recording generated files into this file.
     */
    public void setManifest(File manifestFile)
    {
        manifest.fileValue(manifestFile);
    }

    /**
     * Deep-copy another configuration into this one.
     *
     * @param other the other configuration to copy.
     *
     * @return this configuration.
     */
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
