package au.com.helixta.adl.gradle.config;

import com.github.dockerjava.core.RemoteApiVersion;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

/**
 * Docker configuration that is used when ADL is executed using Docker containers.
 */
public abstract class DockerConfiguration
{
    private URI host;
    private Boolean tlsVerify;
    private final DirectoryProperty certPath = getObjectFactory().directoryProperty();
    private RemoteApiVersion apiVersion;
    private URI registryUrl;
    private String registryUsername;
    private String registryPassword;

    private ImageBuildMode imageBuildMode = ImageBuildMode.USE_EXISTING;

    private Duration imagePullTimeout;
    private Duration imageBuildTimeout;
    private Duration containerExecutionTimeout;

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    /**
     * @return the Docker Host URL, e.g. tcp://localhost:2376 or unix:///var/run/docker.sock
     */
    @Internal
    public URI getHost()
    {
        return host;
    }

    /**
     * Sets the Docker Host URL, e.g. tcp://localhost:2376 or unix:///var/run/docker.sock.
     */
    public void setHost(URI host)
    {
        this.host = host;
    }

    /**
     * Return whether TLS verification is used for Docker communication.
     */
    @Internal
    public Boolean getTlsVerify()
    {
        return tlsVerify;
    }

    /**
     * Enable/disable TLS verification (switch between http and https protocol).
     */
    public void setTlsVerify(Boolean tlsVerify)
    {
        this.tlsVerify = tlsVerify;
    }

    /**
     * @return path to the certificates needed for TLS verification.
     */
    @Internal
    public DirectoryProperty getCertPath()
    {
        return certPath;
    }

    /**
     * Sets the path to the certificates needed for TLS verification.
     */
    public void setCertPath(File certPath)
    {
        this.certPath.fileValue(certPath);
    }

    /**
     * @return the Docker API version to use for communicating with Docker.
     */
    @Internal
    public RemoteApiVersion getApiVersion()
    {
        return apiVersion;
    }

    /**
     * Sets the Docker API version to use for communicating with Docker.
     */
    public void setApiVersion(RemoteApiVersion apiVersion)
    {
        this.apiVersion = apiVersion;
    }

    /**
     * Sets the Docker API version to use for communicating with Docker.
     */
    public void setApiVersion(String apiVersion)
    {
        if (apiVersion == null)
        {
            this.apiVersion = null;
        }
        else
        {
            this.apiVersion = RemoteApiVersion.parseConfig(apiVersion);
        }
    }

    /**
     * Sets the Docker API version to use for communicating with Docker.
     */
    public void setApiVersion(BigDecimal apiVersion)
    {
        if (apiVersion == null)
        {
            this.apiVersion = null;
        }
        else
        {
            this.apiVersion = RemoteApiVersion.parseConfig(apiVersion.toPlainString());
        }
    }

    /**
     * @return the Docker registry URL.
     */
    @Internal
    public URI getRegistryUrl()
    {
        return registryUrl;
    }

    /**
     * Sets the URL for the Docker registry.
     * @param registryUrl
     */
    public void setRegistryUrl(URI registryUrl)
    {
        this.registryUrl = registryUrl;
    }

    /**
     * @return the Docker registry username, used for logging into container registry.
     */
    @Internal
    public String getRegistryUsername()
    {
        return registryUsername;
    }

    /**
     * Sets the Docker registry username, used for logging into container registry.
     */
    public void setRegistryUsername(String registryUsername)
    {
        this.registryUsername = registryUsername;
    }

    /**
     * @return the Docker registry password.
     */
    @Internal
    public String getRegistryPassword()
    {
        return registryPassword;
    }

    /**
     * Sets the Docker registry password.
     */
    public void setRegistryPassword(String registryPassword)
    {
        this.registryPassword = registryPassword;
    }

    /**
     * @return the image build mode which controls when the Docker image for the ADL code generator is built, and whether existing images can be reused.
     */
    @Internal
    public ImageBuildMode getImageBuildMode()
    {
        return imageBuildMode;
    }

    /**
     * Sets the image build mode which controls when the Docker image for the ADL code generator is built, and whether existing images can be reused.
     */
    public void setImageBuildMode(ImageBuildMode imageBuildMode)
    {
        this.imageBuildMode = imageBuildMode;
    }

    /**
     * @return the timeout for pulling images from remote Docker repositories.
     */
    @Internal
    public Duration getImagePullTimeout()
    {
        return imagePullTimeout;
    }

    /**
     * Sets the timeout for pulling images from remote Docker repositories.
     */
    public void setImagePullTimeout(Duration imagePullTimeout)
    {
        this.imagePullTimeout = imagePullTimeout;
    }

    /**
     * @return the timeout for building ADL tool Docker images.  Does not include base image pull time.
     */
    @Internal
    public Duration getImageBuildTimeout()
    {
        return imageBuildTimeout;
    }

    /**
     * Sets the timeout for building ADL tool Docker images.  Does not include base image pull time.
     */
    public void setImageBuildTimeout(Duration imageBuildTimeout)
    {
        this.imageBuildTimeout = imageBuildTimeout;
    }

    /**
     * @return the maximum amount of time an ADL generation execution can take before failing.
     */
    @Internal
    public Duration getContainerExecutionTimeout()
    {
        return containerExecutionTimeout;
    }

    /**
     * Sets the maximum amount of time an ADL generation execution can take before failing.
     */
    public void setContainerExecutionTimeout(Duration containerExecutionTimeout)
    {
        this.containerExecutionTimeout = containerExecutionTimeout;
    }

    /**
     * Deep-copy another configuration into this one.
     *
     * @param other the other configuration to copy.
     */
    public void copyFrom(DockerConfiguration other)
    {
        setHost(other.getHost());
        setTlsVerify(other.getTlsVerify());
        getCertPath().set(other.getCertPath());
        setApiVersion(other.getApiVersion());
        setRegistryUrl(other.getRegistryUrl());
        setRegistryUsername(other.getRegistryUsername());
        setRegistryPassword(other.getRegistryPassword());
        setImageBuildMode(other.getImageBuildMode());
        setImagePullTimeout(other.getImagePullTimeout());
        setImageBuildTimeout(other.getImageBuildTimeout());
        setContainerExecutionTimeout(other.getContainerExecutionTimeout());
    }
}
