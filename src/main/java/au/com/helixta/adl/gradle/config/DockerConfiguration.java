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

    @Internal
    public URI getHost()
    {
        return host;
    }

    public void setHost(URI host)
    {
        this.host = host;
    }

    @Internal
    public Boolean getTlsVerify()
    {
        return tlsVerify;
    }

    public void setTlsVerify(Boolean tlsVerify)
    {
        this.tlsVerify = tlsVerify;
    }

    @Internal
    public DirectoryProperty getCertPath()
    {
        return certPath;
    }

    public void setCertPath(File certPath)
    {
        this.certPath.fileValue(certPath);
    }

    @Internal
    public RemoteApiVersion getApiVersion()
    {
        return apiVersion;
    }

    public void setApiVersion(RemoteApiVersion apiVersion)
    {
        this.apiVersion = apiVersion;
    }

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

    @Internal
    public URI getRegistryUrl()
    {
        return registryUrl;
    }

    public void setRegistryUrl(URI registryUrl)
    {
        this.registryUrl = registryUrl;
    }

    @Internal
    public String getRegistryUsername()
    {
        return registryUsername;
    }

    public void setRegistryUsername(String registryUsername)
    {
        this.registryUsername = registryUsername;
    }

    @Internal
    public String getRegistryPassword()
    {
        return registryPassword;
    }

    public void setRegistryPassword(String registryPassword)
    {
        this.registryPassword = registryPassword;
    }

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

    @Internal
    public Duration getImagePullTimeout()
    {
        return imagePullTimeout;
    }

    public void setImagePullTimeout(Duration imagePullTimeout)
    {
        this.imagePullTimeout = imagePullTimeout;
    }

    @Internal
    public Duration getImageBuildTimeout()
    {
        return imageBuildTimeout;
    }

    public void setImageBuildTimeout(Duration imageBuildTimeout)
    {
        this.imageBuildTimeout = imageBuildTimeout;
    }

    @Internal
    public Duration getContainerExecutionTimeout()
    {
        return containerExecutionTimeout;
    }

    public void setContainerExecutionTimeout(Duration containerExecutionTimeout)
    {
        this.containerExecutionTimeout = containerExecutionTimeout;
    }

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
