package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.util.Arrays;

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
    public ListProperty<File> getSearchDirectories();

    public default void searchDirectory(File... dirs)
    {
        getSearchDirectories().addAll(Arrays.asList(dirs));
    }

    public default void searchDirectory(FileCollection... dirs)
    {
        for (FileCollection dir : dirs)
        {
            //For collections, pull out top-level dir and use that
            dir.getAsFileTree().visit(d ->
                                      {
                                          if (d.isDirectory())
                                          {
                                              getSearchDirectories().add(d.getFile());
                                              d.stopVisiting();
                                          }
                                      });
        }
    }

}
