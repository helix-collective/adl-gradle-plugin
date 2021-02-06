package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class AdlGradlePlugin implements Plugin<Project>
{
    @Override
    public void apply(Project project)
    {
        AdlExtension extension = project.getExtensions().create("adl", AdlExtension.class);

        /*
        AdlPluginExtension extension = project.getExtensions()
                                              .create("adl", AdlPluginExtension.class);

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
