package au.com.helixta.adl.gradle.generator;

public class AdlGenerationException extends Exception
{
    public AdlGenerationException()
    {
    }

    public AdlGenerationException(String message)
    {
        super(message);
    }

    public AdlGenerationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public AdlGenerationException(Throwable cause)
    {
        super(cause);
    }
}
