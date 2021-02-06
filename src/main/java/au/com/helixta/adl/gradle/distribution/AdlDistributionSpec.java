package au.com.helixta.adl.gradle.distribution;

import java.util.StringJoiner;

public class AdlDistributionSpec
{
    private final String version;
    private final String architecture;
    private final String os;

    public AdlDistributionSpec(String version, String architecture, String os)
    {
        this.version = version;
        this.architecture = architecture;
        this.os = os;
    }

    public String getVersion()
    {
        return version;
    }

    public String getArchitecture()
    {
        return architecture;
    }

    public String getOs()
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
