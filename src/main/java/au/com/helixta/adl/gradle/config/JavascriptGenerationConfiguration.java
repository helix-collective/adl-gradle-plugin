package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

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

    public void setManifest(File manifestFile)
    {
        manifest.fileValue(manifestFile);
    }

    public JavascriptGenerationConfiguration copyFrom(JavascriptGenerationConfiguration other)
    {
        super.baseCopyFrom(other);
        setManifest(other.getManifest().getAsFile().getOrNull());
        return this;
    }
}
