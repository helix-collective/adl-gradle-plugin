package au.com.helixta.adl.gradle.containerexecutor;

/**
 * Used to modify a Dockerfile definition for building an image from the default one.
 */
@FunctionalInterface
public interface DockerImageDefinitionTransformer
{
    /**
     * Transformer that does nothing.
     */
    public static final DockerImageDefinitionTransformer NO_MODIFICATION = definition -> {};

    /**
     * Modifies a Docker image definition.
     *
     * @param definition definition to modify.
     */
    public void transform(DockerImageDefinition definition);
}
