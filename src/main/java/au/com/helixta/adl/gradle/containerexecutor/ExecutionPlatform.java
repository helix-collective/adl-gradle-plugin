package au.com.helixta.adl.gradle.containerexecutor;

/**
 * How a tool is executed.
 */
public enum ExecutionPlatform
{
    /**
     * Automatically choose the platform based on the environment.  If native is available use that, otherwise fall back to Docker.
     */
    AUTO,

    /**
     * Run the binary directly on the host OS.  If a native binary is not available for the host OS the build fails.
     */
    NATIVE,

    /**
     * Run using a Docker container.  Requires Docker to be available.
     */
    DOCKER
}
