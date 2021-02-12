package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

public class AdlGradlePlugin implements Plugin<Project>
{
    private final ObjectFactory objectFactory;

    @Inject
    public AdlGradlePlugin(ObjectFactory objectFactory)
    {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(Project project)
    {
        project.getPluginManager().apply(JavaBasePlugin.class);

        AdlExtension extension = project.getExtensions().create("adl", AdlExtension.class);

        //For every source set, add an 'adl' source directory
        //e.g. src/main -> src/main/adl

        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(sourceSet -> {

            SourceDirectorySet adlSource = objectFactory.sourceDirectorySet("adl", sourceSet.getName() +  " ADL source");
            adlSource.getFilter().include("**/*.adl"); //A default filter
            sourceSet.getExtensions().add(SourceDirectorySet.class, "adl", adlSource);

            adlSource.srcDir("src/" + sourceSet.getName() + "/adl");

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            final FileCollection adlSourceFiles = adlSource;
            sourceSet.getResources().getFilter().exclude(
                    spec(element -> adlSourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllSource().source(adlSource);

            String taskName = sourceSet.getTaskName("generate", "Adl");
            project.getTasks().register(taskName, AdlGenerateTask.class, adlTask ->
            {
                adlTask.copyFrom(extension);
                adlTask.source(adlSourceFiles);

                //Auto-configure the output directories if not explicitly defined
                for (GenerationConfiguration generation : adlTask.getGenerations().allGenerations())
                {
                    if (!generation.getOutputDirectory().isPresent())
                    {
                        //e.g. build/generated/sources/adl/java/main/
                        Provider<Directory> outputDirectory = project.getLayout().getBuildDirectory().dir(
                                                                "generated/sources/adl/" +
                                                                generation.generationType() + "/" +
                                                                sourceSet.getName());
                        generation.getOutputDirectory().set(outputDirectory);
                    }

                    //Add output of Java ADL generation to Java input source dirs
                    //(special case to support Java plugin so user doesn't have to manually configure extra source dir for it)
                    if (generation instanceof JavaGenerationConfiguration)
                        sourceSet.getJava().srcDir(generation.getOutputDirectory());
                }
            });

            //Make the Java compile task depend on this if it has Java generators
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), compileJavaTask ->
            {
                if (extension.getGenerations() != null && !extension.getGenerations().getJava().isEmpty())
                    compileJavaTask.dependsOn(taskName);
            });
        });
    }
}
