package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.AdlPluginExtension;
import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;

import java.io.Closeable;

/**
 * Generates ADL.
 */
public interface AdlGenerator extends Closeable
{
    public void generate(AdlConfiguration configuration, Iterable<? extends GenerationConfiguration> generations)
    throws AdlGenerationException;
}
