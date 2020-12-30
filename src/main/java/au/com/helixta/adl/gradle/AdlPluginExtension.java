package au.com.helixta.adl.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AdlPluginExtension implements ExtensionAware
{
    @Nested
    private final Generations generations = getExtensions().create("generations", Generations.class);

    public Generations getGenerations()
    {
        return generations;
    }

    public void generations(Action<? super Generations> configuration)
    {
        configuration.execute(generations);
    }

    public abstract static class Generations implements ExtensionAware
    {
        @Nested
        private final List<JavaGeneration> java = new ArrayList<>();

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        public List<JavaGeneration> getJava()
        {
            return java;
        }

        public void java(Action<? super JavaGeneration> configuration)
        {
            JavaGeneration j = getObjectFactory().newInstance(JavaGeneration.class);
            configuration.execute(j);
            java.add(j);
        }
    }

    public static abstract class Generation
    {
        private final DirectoryProperty outputDirectory = getObjectFactory().directoryProperty();

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @OutputDirectory
        public DirectoryProperty getOutputDirectory()
        {
            return outputDirectory;
        }
    }

    public abstract static class JavaGeneration extends Generation
    {

    }
}
