package au.com.helixta.adl.gradle.config;

/**
 * Controls when an ADL Docker image is built.
 */
public enum ImageBuildMode
{
    /**
     * If a Docker image already exists, use it.
     */
    USE_EXISTING,

    /**
     * Rebuild or re-download the Docker image if one exists locally that was previously generated.
     * Will use a remote image if it exists or was downloaded previously.
     */
    DISCARD_LOCAL,

    /**
     * Rebuilds the Docker image unconditionally, not using any existing remote one existing in a repository.
     */
    REBUILD
}
