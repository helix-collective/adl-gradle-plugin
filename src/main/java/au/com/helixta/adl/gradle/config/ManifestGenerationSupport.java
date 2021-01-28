package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;

public interface ManifestGenerationSupport
{
    public RegularFileProperty getManifest();
}
