package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.AdlPluginExtension;

import java.io.Closeable;

public interface AdlGenerator extends Closeable
{
    public void generate(Iterable<? extends AdlPluginExtension.Generation> generations)
    throws AdlGenerationException;
}
