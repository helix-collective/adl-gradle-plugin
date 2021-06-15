package au.com.helixta.adl.gradle.containerexecutor;

import java.util.Locale;

public enum AdlCompilerFileLabel implements FileLabel
{
    /**
     * ADL source files.
     */
    SOURCES;

    @Override
    public String getLabel()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
