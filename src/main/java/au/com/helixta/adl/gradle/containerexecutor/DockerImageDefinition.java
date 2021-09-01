package au.com.helixta.adl.gradle.containerexecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Docker image definition is used to build a Dockerfile that can be used to build an image.  To use,
 * once constructed add labels using {@link #getLabels()} and other arbitrary Dockerfile commands by adding to the list
 * returned from {@link #getCommands()}, then generate the Dockerfile using {@link #toDockerFile()}.
 *
 * @see <a href="https://docs.docker.com/engine/reference/builder/">Dockerfile reference</a>
 */
public class DockerImageDefinition
{
    private String baseImage;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final List<String> commands = new ArrayList<>();

    /**
     * Creates a docker image definition with the specified base image.
     *
     * @param baseImage name of the base image.  e.g. 'ubuntu:20.04'
     *
     * @see <a href="https://docs.docker.com/engine/reference/builder/#from">Dockerfile FROM reference</a>
     */
    public DockerImageDefinition(String baseImage)
    {
        this.baseImage = Objects.requireNonNull(baseImage);
    }

    /**
     * @return the base image this dockerfile extends from.
     *
     * @see #setBaseImage(String)
     * @see <a href="https://docs.docker.com/engine/reference/builder/#from">Dockerfile FROM reference</a>
     *
     */
    public String getBaseImage()
    {
        return baseImage;
    }

    /**
     * Sets the base image this dockerfile extends from.
     *
     * @param baseImage the name of the base image to set.
     *
     * @see #getBaseImage()
     * @see <a href="https://docs.docker.com/engine/reference/builder/#from">Dockerfile FROM reference</a>
     */
    public void setBaseImage(String baseImage)
    {
        this.baseImage = Objects.requireNonNull(baseImage);
    }

    /**
     * @return a modifiable map of labels that will be applied to the Dockerfile.
     *
     * @see <a href="https://docs.docker.com/engine/reference/builder/#label">Dockerfile LABEL reference</a>
     */
    public Map<String, String> getLabels()
    {
        return labels;
    }

    /**
     * @return a modifiable list of commands that will be applied to the Dockerfile.  Do not include FROM or LABEL commands as these are sourced elsewhere.  Do not include line
     * breaks.
     */
    public List<String> getCommands()
    {
        return commands;
    }

    /**
     * Generates a Dockerfile for this definition.
     *
     * @return Dockerfile content.
     *
     * @see <a href="https://docs.docker.com/engine/reference/builder/">Dockerfile reference</a>
     */
    public String toDockerFile()
    {
        StringBuilder buf = new StringBuilder();

        String newline = "\n";

        buf.append("FROM ").append(getBaseImage()).append(newline);
        for (Map.Entry<String, String> entry : getLabels().entrySet())
        {
            buf.append("LABEL ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"").append(newline);
        }
        for (String command : getCommands())
        {
            buf.append(command).append(newline);
        }

        return buf.toString();
    }
}
