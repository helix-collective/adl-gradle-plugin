package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;

public interface AdlConfiguration
{
    public FileTree getSource();
    public FileCollection getSearchDirectories();
    public boolean isVerbose();

    /**
     * @return the version of ADL to use.
     */
    public String getVersion();
}
