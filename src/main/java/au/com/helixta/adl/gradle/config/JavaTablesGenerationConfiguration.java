package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

/**
 * Java Helix SQL tables code generation configuration for ADL.
 */
public abstract class JavaTablesGenerationConfiguration extends GenerationConfiguration implements ManifestGenerationSupport
{
    private String javaPackage;
    private String adlRuntimePackage;
    //TODO

    private final RegularFileProperty manifest = getObjectFactory().fileProperty();

    public JavaTablesGenerationConfiguration()
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
    public JavaTablesGenerationConfiguration copyFrom(JavaTablesGenerationConfiguration other)
    {
        super.baseCopyFrom(other);
        setJavaPackage(other.getJavaPackage());
        setAdlRuntimePackage(other.getAdlRuntimePackage());
        setManifest(other.getManifest().getAsFile().getOrNull());
        return this;
    }
}
