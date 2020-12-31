package au.com.helixta.adl.gradle;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class AdlGenerateJavaTask extends DefaultTask
{
    private final AdlPluginExtension.Generations generations;

    @Inject
    public AdlGenerateJavaTask(ObjectFactory objectFactory)
    {
        //this.generations =  getExtensions().create("generations", AdlPluginExtension.Generations.class, objectFactory);
        this.generations = objectFactory.newInstance(AdlPluginExtension.Generations.class);
    }

    @Nested
    public AdlPluginExtension.Generations getGenerations()
    {
        return generations;
    }

    public void generations(Action<? super AdlPluginExtension.Generations> configuration)
    {
        configuration.execute(generations);
    }

    @TaskAction
    public void generateJava()
    {
        for (AdlPluginExtension.JavaGeneration java : getGenerations().getJava())
        {
            System.out.println("Generate java: " + java.getOutputDirectory().get());
            System.out.println("   Files: " + java.getSourcepath().getFiles());
            System.out.println("   Search dirs: " + java.getSearchDirectories());
        }
    }
}
