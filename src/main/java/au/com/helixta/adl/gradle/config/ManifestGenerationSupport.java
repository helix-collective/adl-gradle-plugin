package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;

public interface ManifestGenerationSupport
{
    /**
     * @return the manifest file recording generated files, or null if not used.
     */
    public RegularFileProperty getManifest();
}
