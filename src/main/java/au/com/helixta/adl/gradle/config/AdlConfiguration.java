package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;

import java.io.File;

public interface AdlConfiguration
{
    public FileTree getSource();
    public ListProperty<File> getSearchDirectories();
    public boolean isVerbose();

    /**
     * @return the version of ADL to use.
     */
    public String getVersion();
}
