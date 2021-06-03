package au.com.helixta.adl.gradle.distribution;

/**
 * Occurs when a distribution for a required specification is not available.
 */
public class DistributionNotFoundException extends Exception
{
    public DistributionNotFoundException()
    {
    }

    public DistributionNotFoundException(String message)
    {
        super(message);
    }

    public DistributionNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public DistributionNotFoundException(Throwable cause)
    {
        super(cause);
    }
}
