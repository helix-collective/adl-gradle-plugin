package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.FileTree;

import java.io.File;
import java.util.List;

public interface AdlConfiguration
{
    public FileTree getSourcepath();
    public List<File> getSearchDirectories();
    public boolean isVerbose();
}
