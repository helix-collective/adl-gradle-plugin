package au.com.helixta.adl.gradle;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationsConfiguration;
import org.gradle.api.Action;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Nested;

public abstract class AdlPluginExtension implements ExtensionAware
{
    private final GenerationsConfiguration generations = getExtensions().create("generations", GenerationsConfiguration.class);
    private final DockerConfiguration docker = getExtensions().create("docker", DockerConfiguration.class);

    @Nested
    public GenerationsConfiguration getGenerations()
    {
        return generations;
    }

    @Nested
    public DockerConfiguration getDocker()
    {
        return docker;
    }

    public void generations(Action<? super GenerationsConfiguration> configuration)
    {
        configuration.execute(generations);
    }

    public void docker(Action<? super DockerConfiguration> configuration)
    {
        configuration.execute(docker);
    }
}
