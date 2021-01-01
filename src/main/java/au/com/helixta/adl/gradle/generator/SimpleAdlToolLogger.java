package au.com.helixta.adl.gradle.generator;

import org.gradle.api.logging.Logger;

import java.util.Objects;

public class SimpleAdlToolLogger implements AdlToolLogger
{
    private final Logger log;

    public SimpleAdlToolLogger(Logger log)
    {
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public void info(String toolName, String message)
    {
        log.info(formatMessage(toolName, message));
    }

    @Override
    public void error(String toolName, String message)
    {
        log.error(formatMessage(toolName, message));
    }

    protected String formatMessage(String toolName, String message)
    {
        return toolName + "> " + message;
    }
}
