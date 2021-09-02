package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.generator.AdlToolLogger;

public class ConsoleAdlToolLogger implements AdlToolLogger
{
    @Override
    public void info(String toolName, String message)
    {
        System.out.println(toolName + "> " + message);
    }

    @Override
    public void error(String toolName, String message)
    {
        System.err.println(toolName + "> " + message);
    }

    @Override
    public boolean isInfoEnabled()
    {
        return true;
    }
}
