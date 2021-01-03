package au.com.helixta.adl.gradle;

import com.github.dockerjava.core.RemoteApiVersion;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AdlPluginExtension implements ExtensionAware
{
    private final Generations generations = getExtensions().create("generations", Generations.class);
    private final DockerConfiguration docker = getExtensions().create("docker", DockerConfiguration.class);

    @Nested
    public Generations getGenerations()
    {
        return generations;
    }

    @Nested
    public DockerConfiguration getDocker()
    {
        return docker;
    }

    public void generations(Action<? super Generations> configuration)
    {
        configuration.execute(generations);
    }

    public void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(docker);
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

        private final List<File> searchDirectories = new ArrayList<>();

        private boolean verbose;

        private final List<String> compilerArgs = new ArrayList<>();

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

        public void setOutputDirectory(File outputDirectory)
        {
            this.outputDirectory.fileValue(outputDirectory);
        }

        @InputFiles
        @Optional
        public List<File> getSearchDirectories()
        {
            return searchDirectories;
        }

        public Generation searchDirectory(File... dirs)
        {
            searchDirectories.addAll(Arrays.asList(dirs));
            return this;
        }

        public Generation searchDirectory(FileCollection... dirs)
        {
            for (FileCollection dir : dirs)
            {
                //For collections, pull out top-level dir and use that
                dir.getAsFileTree().visit(d ->
                {
                    if (d.isDirectory())
                    {
                        searchDirectories.add(d.getFile());
                        d.stopVisiting();
                    }
                });
            }
            return this;
        }

        @Console
        public boolean isVerbose()
        {
            return verbose;
        }

        public void setVerbose(boolean verbose)
        {
            this.verbose = verbose;
        }

        @Input
        public List<String> getCompilerArgs()
        {
            return compilerArgs;
        }

        public void setCompilerArgs(List<String> compilerArgs)
        {
            this.compilerArgs.clear();
            this.compilerArgs.addAll(compilerArgs);
        }
    }

    public abstract static class JavaGeneration extends Generation
    {
        private String javaPackage;
        private String adlRuntimePackage;
        private boolean generateAdlRuntime;
        private boolean generateTransitive;
        private String suppressWarningsAnnotation;
        private String headerComment;

        private final RegularFileProperty manifest = getObjectFactory().fileProperty();

        @Override
        protected String generationType()
        {
            return "java";
        }

        @Input
        public String getJavaPackage()
        {
            return javaPackage;
        }

        public void setJavaPackage(String javaPackage)
        {
            this.javaPackage = javaPackage;
        }

        @Input
        @Optional
        public String getAdlRuntimePackage()
        {
            return adlRuntimePackage;
        }

        public void setAdlRuntimePackage(String adlRuntimePackage)
        {
            this.adlRuntimePackage = adlRuntimePackage;
        }

        @Input
        public boolean isGenerateAdlRuntime()
        {
            return generateAdlRuntime;
        }

        public void setGenerateAdlRuntime(boolean generateAdlRuntime)
        {
            this.generateAdlRuntime = generateAdlRuntime;
        }

        @Input
        public boolean isGenerateTransitive()
        {
            return generateTransitive;
        }

        public void setGenerateTransitive(boolean generateTransitive)
        {
            this.generateTransitive = generateTransitive;
        }

        @Input
        @Optional
        public String getSuppressWarningsAnnotation()
        {
            return suppressWarningsAnnotation;
        }

        public void setSuppressWarningsAnnotation(String suppressWarningsAnnotation)
        {
            this.suppressWarningsAnnotation = suppressWarningsAnnotation;
        }

        @OutputFile
        @Optional
        public RegularFileProperty getManifest()
        {
            return manifest;
        }

        public void setManifest(File manifestFile)
        {
            manifest.fileValue(manifestFile);
        }

        @Input
        @Optional
        public String getHeaderComment()
        {
            return headerComment;
        }

        public void setHeaderComment(String headerComment)
        {
            this.headerComment = headerComment;
        }
    }

    public abstract static class DockerConfiguration
    {
        private URI host;
        private Boolean tlsVerify;
        private final DirectoryProperty certPath = getObjectFactory().directoryProperty();
        private RemoteApiVersion apiVersion;

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @Inject
        protected abstract ProjectLayout getProjectLayout();

        @Optional
        @Internal
        public URI getHost()
        {
            return host;
        }

        public void setHost(URI host)
        {
            this.host = host;
        }

        @Internal
        public Boolean getTlsVerify()
        {
            return tlsVerify;
        }

        public void setTlsVerify(Boolean tlsVerify)
        {
            this.tlsVerify = tlsVerify;
        }

        @Optional
        @Internal
        public DirectoryProperty getCertPath()
        {
            return certPath;
        }

        public void setCertPath(File certPath)
        {
            this.certPath.fileValue(certPath);
        }

        @Optional
        @Internal
        public RemoteApiVersion getApiVersion()
        {
            return apiVersion;
        }

        public void setApiVersion(RemoteApiVersion apiVersion)
        {
            this.apiVersion = apiVersion;
        }

        public void setApiVersion(String apiVersion)
        {
            if (apiVersion == null)
                this.apiVersion = null;
            else
                this.apiVersion = RemoteApiVersion.parseConfig(apiVersion);
        }

        public void setApiVersion(BigDecimal apiVersion)
        {
            if (apiVersion == null)
                this.apiVersion = null;
            else
                this.apiVersion = RemoteApiVersion.parseConfig(apiVersion.toPlainString());
        }
    }
}
