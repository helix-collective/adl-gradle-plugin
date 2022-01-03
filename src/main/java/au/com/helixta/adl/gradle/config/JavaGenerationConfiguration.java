package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

/**
 * Java code generation configuration for ADL.
 */
public abstract class JavaGenerationConfiguration extends GenerationConfiguration implements ManifestGenerationSupport
{
    private String javaPackage;
    private String adlRuntimePackage;
    private boolean generateAdlRuntime;
    private boolean generateTransitive;
    private String suppressWarningsAnnotation;
    private String headerComment;

    private final RegularFileProperty manifest = getObjectFactory().fileProperty();

    public JavaGenerationConfiguration()
    {
        super("java");
    }

    /**
     * @return the name of the Java package the generated code will have.
     */
    @Input
    public String getJavaPackage()
    {
        return javaPackage;
    }

    /**
     * Sets the name of the Java package the generated code will have.
     */
    public void setJavaPackage(String javaPackage)
    {
        this.javaPackage = javaPackage;
    }

    /**
     * @return the language package where the ADL runtime is located.
     */
    @Input
    @Optional
    public String getAdlRuntimePackage()
    {
        return adlRuntimePackage;
    }

    /**
     * Sets the language package where the ADL runtime is located.
     */
    public void setAdlRuntimePackage(String adlRuntimePackage)
    {
        this.adlRuntimePackage = adlRuntimePackage;
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
     * @return The @SuppressWarnings annotation to be generated (comma separated).
     */
    @Input
    @Optional
    public String getSuppressWarningsAnnotation()
    {
        return suppressWarningsAnnotation;
    }

    /**
     * Sets the @SuppressWarnings annotation to be generated (comma separated).
     */
    public void setSuppressWarningsAnnotation(String suppressWarningsAnnotation)
    {
        this.suppressWarningsAnnotation = suppressWarningsAnnotation;
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
     * @return the comment to be placed at the start of each Java file.
     */
    @Input
    @Optional
    public String getHeaderComment()
    {
        return headerComment;
    }

    /**
     * Sets the comment to be placed at the start of each Java file.
     */
    public void setHeaderComment(String headerComment)
    {
        this.headerComment = headerComment;
    }

    /**
     * Deep-copy another configuration into this one.
     *
     * @param other the other configuration to copy.
     *
     * @return this configuration.
     */
    public JavaGenerationConfiguration copyFrom(JavaGenerationConfiguration other)
    {
        super.baseCopyFrom(other);
        setJavaPackage(other.getJavaPackage());
        setAdlRuntimePackage(other.getAdlRuntimePackage());
        setGenerateAdlRuntime(other.isGenerateAdlRuntime());
        setGenerateTransitive(other.isGenerateTransitive());
        setSuppressWarningsAnnotation(other.getSuppressWarningsAnnotation());
        setManifest(other.getManifest().getAsFile().getOrNull());
        setHeaderComment(other.getHeaderComment());
        return this;
    }
}
