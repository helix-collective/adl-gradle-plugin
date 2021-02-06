package au.com.helixta.adl.gradle.distribution;

/**
 * Occurs when an ADL distribution for a required specification is not available.
 */
public class AdlDistributionNotFoundException extends Exception
{
    public AdlDistributionNotFoundException()
    {
    }

    public AdlDistributionNotFoundException(String message)
    {
        super(message);
    }

    public AdlDistributionNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public AdlDistributionNotFoundException(Throwable cause)
    {
        super(cause);
    }
}
