package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;

import java.io.IOException;

@FunctionalInterface
public interface ExecutableResolver
{
    public String resolveExecutable(String distributionBaseDirectory, DistributionSpecifier distributionSpecifier)
    throws IOException;
}
