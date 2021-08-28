package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.DockerConfiguration;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.sun.jna.Platform;

import java.util.Objects;

public class DockerClientFactory
{
    private final DockerClientConfig config;

    public DockerClientFactory(DockerConfiguration dockerConfiguration)
    {
        this(dockerClientConfig(dockerConfiguration));
    }

    public DockerClientFactory(DockerClientConfig config)
    {
        this.config = Objects.requireNonNull(config);
    }

    public DockerClient createDockerClient()
    {
        //For Windows, don't even try using a unix: socket
        if (config.getDockerHost() != null && "unix".equals(config.getDockerHost().getScheme()) && Platform.isWindows())
            throw new RuntimeException("Docker on Windows platform has not been configured. The ADL code generator requires Docker for running on this platform. Install Docker and configure environment variables such as DOCKER_HOST appropriately.");

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                                            .dockerHost(config.getDockerHost())
                                            .sslConfig(config.getSSLConfig())
                                            .build();

        DockerClient docker = DockerClientImpl.getInstance(config, httpClient);

        //Test that Docker is working on this platform
        try
        {
            docker.pingCmd().exec();
        }
        catch (RuntimeException e)
        {
            //Unfortunately exceptions are wrapped in runtime exceptions, just treat any runtime exception as failure
            throw new RuntimeException("Docker is not functioning properly - ADL generation failed. Check Docker configuration. " + e.getMessage(), e);
        }

        //If we get here Docker is functional on this platform
        return docker;
    }

    /**
     * Creates DockerJava configuration from an ADL Gradle plugin Docker configuration object.
     *
     * @param config the ADL Gradle plugin Docker configuration object.
     *
     * @return a DockerJava configuration object.
     */
    private static DockerClientConfig dockerClientConfig(DockerConfiguration config)
    {
        //By default, everything configured from environment, such as environment variables
        DefaultDockerClientConfig.Builder c = DefaultDockerClientConfig.createDefaultConfigBuilder();

        //Augment with anything overridden from the docker configuration
        if (config.getHost() != null)
            c.withDockerHost(config.getHost().toString());
        if (config.getTlsVerify() != null)
            c.withDockerTlsVerify(config.getTlsVerify());
        if (config.getCertPath().isPresent())
            c.withDockerCertPath(config.getCertPath().getAsFile().get().getAbsolutePath());
        if (config.getApiVersion() != null)
            c.withApiVersion(config.getApiVersion());
        if (config.getRegistryUrl() != null)
            c.withRegistryUrl(config.getRegistryUrl().toString());
        if (config.getRegistryUsername() != null)
            c.withRegistryUsername(config.getRegistryUsername());
        if (config.getRegistryPassword() != null)
            c.withRegistryPassword(config.getRegistryPassword());

        return c.build();
    }
}
