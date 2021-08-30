package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import java.io.File;

public interface AdlExtension
{
    @Nested
    public GenerationsConfiguration getGenerations();

    public default void generations(Action<? super GenerationsConfiguration> configuration)
    {
        configuration.execute(getGenerations());
    }

    @Nested
    public DockerConfiguration getDocker();

    public default void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(getDocker());
    }

    @Console
    public boolean isVerbose();
    public void setVerbose(boolean verbose);

    @InputFiles
    @Optional
    public ConfigurableFileCollection getSearchDirectories();

    public default void searchDirectory(File... dirs)
    {
        getSearchDirectories().from((Object[])dirs);
    }

    public default void searchDirectory(FileCollection... dirs)
    {
        getSearchDirectories().from((Object[])dirs);
    }

    /**
     * @return the version of ADL to use.
     *
     * @see <a href="https://github.com/timbod7/adl/releases">ADL Releases</a>
     */
    @Input
    public String getVersion();

    /**
     * Sets the version of ADL to use.
     *
     * @see <a href="https://github.com/timbod7/adl/releases">ADL Releases</a>
     */
    public void setVersion(String version);

    @Internal
    @Optional
    public ExecutionPlatform getPlatform();

    public void setPlatform(ExecutionPlatform platform);

    public default AdlExtension copyFrom(AdlExtension other)
    {
        setVerbose(other.isVerbose());
        setVersion(other.getVersion());
        setPlatform(other.getPlatform());
        getSearchDirectories().from(other.getSearchDirectories());
        getGenerations().copyFrom(other.getGenerations());
        getDocker().copyFrom(other.getDocker());
        return this;
    }
}
