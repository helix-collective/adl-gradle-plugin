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
    /**
     * @return multiple code generations.
     */
    @Nested
    public GenerationsConfiguration getGenerations();

    /**
     * Configure the source code to generate from ADL.
     */
    public default void generations(Action<? super GenerationsConfiguration> configuration)
    {
        configuration.execute(getGenerations());
    }

    /**
     * @return Docker configuration that is used when ADL is executed using Docker containers.
     */
    @Nested
    public DockerConfiguration getDocker();

    /**
     * Docker configuration that is used when ADL is executed using Docker containers.
     */
    public default void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(getDocker());
    }

    /**
     * @return whether to run the ADL compiler in verbose mode.
     */
    @Console
    public boolean isVerbose();

    /**
     * Sets whether the ADL compiler is run in verbose mode.
     */
    public void setVerbose(boolean verbose);

    /**
     * @return ADL search directories which are used for locating additional ADL files.
     */
    @InputFiles
    @Optional
    public ConfigurableFileCollection getSearchDirectories();

    /**
     * Adds ADL search directories which are used for locating additional ADL files.
     */
    public default void searchDirectory(File... dirs)
    {
        getSearchDirectories().from((Object[])dirs);
    }

    /**
     * Adds ADL search directories which are used for locating additional ADL files.
     */
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

    /**
     * @return the platform to use for executing the ADL compiler.  Can be used to switch between native and Docker execution.
     */
    @Internal
    @Optional
    public ExecutionPlatform getPlatform();

    /**
     * Sets the platform to use for executing the ADL compiler.  Can be used to switch between native and Docker execution.
     * @param platform
     */
    public void setPlatform(ExecutionPlatform platform);

    /**
     * Deep-copy another configuration into this one.
     *
     * @param other the other configuration to copy.
     *
     * @return this configuration.
     */
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
