package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;

import java.io.IOException;

public interface ContainerExecutor
{
    public void execute(PreparedCommandLine commandLine)
    throws IOException, DistributionNotFoundException, ContainerExecutionException;
}
