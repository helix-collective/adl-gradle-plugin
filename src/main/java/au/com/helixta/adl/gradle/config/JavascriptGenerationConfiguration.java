package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

/**
 * Javascript code generation configuration for ADL.
 */
public abstract class JavascriptGenerationConfiguration extends GenerationConfiguration implements ManifestGenerationSupport
{
    private final RegularFileProperty manifest = getObjectFactory().fileProperty();

    public JavascriptGenerationConfiguration()
    {
        super("javascript");
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
    public JavascriptGenerationConfiguration copyFrom(JavascriptGenerationConfiguration other)
    {
        super.baseCopyFrom(other);
        setManifest(other.getManifest().getAsFile().getOrNull());
        return this;
    }
}
