package au.com.helixta.adl.gradle.generator;

import org.gradle.internal.logging.text.StyledTextOutput;

public class ColoredAdlToolLogger implements AdlToolLogger
{
    private final StyledTextOutput out;
    private final StyledTextOutput err;

    public ColoredAdlToolLogger(StyledTextOutput out, StyledTextOutput err)
    {
        this.out = out;
        this.err = err;
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
}
