package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;

import javax.inject.Inject;

import static org.gradle.api.internal.lambdas.SerializableLambdas.*;

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
            DefaultAdlSourceSet adlSourceSet = new DefaultAdlSourceSet("adl", ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
            SourceDirectorySet adlSource = adlSourceSet.getAdl();
            sourceSet.getExtensions().add(SourceDirectorySet.class, "adl", adlSource);

            adlSource.srcDir("src/" + sourceSet.getName() + "/adl");

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            final FileCollection adlSourceFiles = adlSource;
            sourceSet.getResources().getFilter().exclude(
                    spec(element -> adlSourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllSource().source(adlSource);

            //TODO also configure ADL generation task
        });


        /*
        project.task("adl")
               .doLast(task -> this.runAdl(task, extension));
         */
    }

    private void runAdl(Task task, AdlPluginExtension config)
    {
        for (JavaGenerationConfiguration generation : config.getGenerations().getJava())
        {
            generation.getOutputDirectory().convention(task.getProject().getLayout().getBuildDirectory().dir("generated/adl"));
            System.out.println("Generation: " + generation.getOutputDirectory().get());
        }
    }
}
