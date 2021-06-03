package au.com.helixta.adl.gradle.distribution;

import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.OperatingSystem;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Parameters for specifying a distribution - distribution version, operating system type and machine architecture.
 */
public class DistributionSpecifier
{
    private final String version;
    private final Architecture architecture;
    private final OperatingSystem os;

    /**
     * Creates a distribution specifier.
     *
     * @param version version of the distribution.
     * @param architecture machine architecture the distribution runs on.
     * @param os operating system the distribution runs on.
     */
    public DistributionSpecifier(String version, Architecture architecture, OperatingSystem os)
    {
        this.version = Objects.requireNonNull(version);
        this.architecture = Objects.requireNonNull(architecture);
        this.os = Objects.requireNonNull(os);
    }

    /**
     * @return the version of the distribution.
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * @return the machine architecture the distribution runs on.
     */
    public Architecture getArchitecture()
    {
        return architecture;
    }

    /**
     * @return the operating system the distribution runs on.
     */
    public OperatingSystem getOs()
    {
        return os;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", DistributionSpecifier.class.getSimpleName() + "[", "]")
                .add("version='" + version + "'")
                .add("architecture='" + architecture + "'")
                .add("os='" + os + "'")
                .toString();
    }
}
