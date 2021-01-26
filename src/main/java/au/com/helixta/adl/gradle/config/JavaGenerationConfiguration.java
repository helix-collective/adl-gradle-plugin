package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

public abstract class JavaGenerationConfiguration extends GenerationConfiguration
{
    private String javaPackage;
    private String adlRuntimePackage;
    private boolean generateAdlRuntime;
    private boolean generateTransitive;
    private String suppressWarningsAnnotation;
    private String headerComment;

    private final RegularFileProperty manifest = getObjectFactory().fileProperty();

    public JavaGenerationConfiguration()
    {
        super("java");
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
