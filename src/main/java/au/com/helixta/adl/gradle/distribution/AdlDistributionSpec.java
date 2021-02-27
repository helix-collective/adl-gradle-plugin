package au.com.helixta.adl.gradle.distribution;

import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.OperatingSystem;

import java.util.StringJoiner;

public class AdlDistributionSpec
{
    private final String version;
    private final Architecture architecture;
    private final OperatingSystem os;

    public AdlDistributionSpec(String version, Architecture architecture, OperatingSystem os)
    {
        this.version = version;
        this.architecture = architecture;
        this.os = os;
    }

    public String getVersion()
    {
        return version;
    }

    public Architecture getArchitecture()
    {
        return architecture;
    }

    public OperatingSystem getOs()
    {
        return os;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", AdlDistributionSpec.class.getSimpleName() + "[", "]")
                .add("version='" + version + "'")
                .add("architecture='" + architecture + "'")
                .add("os='" + os + "'")
                .toString();
    }
}
