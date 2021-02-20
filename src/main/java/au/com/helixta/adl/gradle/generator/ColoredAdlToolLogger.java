package au.com.helixta.adl.gradle.generator;

import org.gradle.internal.logging.text.StyledTextOutput;

public class ColoredAdlToolLogger implements AdlToolLogger
{
    private final StyledTextOutput out;
    private final StyledTextOutput err;
    private final boolean outLoggingEnabled;

    public ColoredAdlToolLogger(StyledTextOutput out, StyledTextOutput err, boolean outLoggingEnabled)
    {
        this.out = out;
        this.err = err;
        this.outLoggingEnabled = outLoggingEnabled;
    }

    @Override
    public void info(String toolName, String message)
    {
        out.style(StyledTextOutput.Style.Header)
           .text(toolName + "> ")
           .style(StyledTextOutput.Style.Normal)
           .text(message)
           .println();
    }

    @Override
    public void error(String toolName, String message)
    {
        err.style(StyledTextOutput.Style.Header)
           .text(toolName + "> ")
           .style(StyledTextOutput.Style.Error)
           .text(message)
           .println();
    }

    @Override
    public boolean isInfoEnabled()
    {
        return outLoggingEnabled;
    }
}
