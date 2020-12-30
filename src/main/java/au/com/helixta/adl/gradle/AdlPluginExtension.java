package au.com.helixta.adl.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class AdlPluginExtension implements ExtensionAware
{
    private final Generations generations = getExtensions().create("generations", Generations.class);

    @Nested
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
        private final List<JavaGeneration> java = new ArrayList<>();

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @Nested
        @SkipWhenEmpty
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
        private final DirectoryProperty outputDirectory =
                getObjectFactory().directoryProperty()
                    .convention(getProjectLayout().getBuildDirectory().dir("generated/adl/" + generationType()));

        private final ConfigurableFileCollection sourcepath =
                getObjectFactory().fileCollection();

        private final PatternSet patternSet = getPatternSetFactory().create().include("**/*.adl");

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @Inject
        protected abstract ProjectLayout getProjectLayout();

        @Inject
        protected abstract Factory<PatternSet> getPatternSetFactory();

        protected abstract String generationType();

        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFiles
        public FileTree getSourcepath()
        {
            return sourcepath.getAsFileTree().matching(patternSet);
        }

        public void sourcepath(File... sources)
        {
            sourcepath.from((Object[]) sources);
        }

        public void sourcepath(FileCollection... sources)
        {
            sourcepath.from((Object[]) sources);
        }

        public Generation include(String... includes)
        {
            patternSet.include(includes);
            return this;
        }

        public Generation include(Iterable<String> includes)
        {
            patternSet.include(includes);
            return this;
        }

        public Generation exclude(String... excludes)
        {
            patternSet.exclude(excludes);
            return this;
        }

        public Generation exclude(Iterable<String> excludes)
        {
            patternSet.exclude(excludes);
            return this;
        }

        @OutputDirectory
        public DirectoryProperty getOutputDirectory()
        {
            return outputDirectory;
        }
    }

    public abstract static class JavaGeneration extends Generation
    {
        @Override
        protected String generationType()
        {
            return "java";
        }
    }
}
