package au.com.helixta.adl.gradle.generator;

public interface AdlToolLogger
{
    public void info(String toolName, String message);
    public void error(String toolName, String message);
}
