package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.containerexecutor.AdlContainerTool;
import au.com.helixta.adl.gradle.containerexecutor.ContainerExecutionException;
import au.com.helixta.adl.gradle.containerexecutor.ContainerTool;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;

import java.io.IOException;
import java.util.Objects;

public class AdlToolGenerator implements AdlGenerator
{
    private final AdlContainerTool adlTool;
    private final DockerConfiguration dockerConfiguration;
    private final ContainerTool.ExecutionPlatform platform;

    public AdlToolGenerator(ContainerTool.Environment environment, DockerConfiguration dockerConfiguration, ContainerTool.ExecutionPlatform platform)
    {
        this.adlTool = new AdlContainerTool(environment);
        this.dockerConfiguration = Objects.requireNonNull(dockerConfiguration);
        this.platform = Objects.requireNonNull(platform);
    }

    @Override
    public void generate(AdlConfiguration configuration, Iterable<? extends GenerationConfiguration> generations)
    throws AdlGenerationException
    {
        for (GenerationConfiguration generation : generations)
        {
            try
            {
                adlTool.execute(new AdlContainerTool.AdlFullConfiguration(configuration, generation, dockerConfiguration), platform);
            }
            catch (IOException | ContainerExecutionException | DistributionNotFoundException e)
            {
                throw new AdlGenerationException(e);
            }
        }
    }

    @Override
    public void close() throws IOException
    {
        //TODO close docker client?
    }
}
