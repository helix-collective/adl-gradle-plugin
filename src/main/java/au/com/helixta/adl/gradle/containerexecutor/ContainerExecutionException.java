package au.com.helixta.adl.gradle.containerexecutor;

public class ContainerExecutionException extends Exception
{
    public ContainerExecutionException()
    {
    }

    public ContainerExecutionException(String message)
    {
        super(message);
    }

    public ContainerExecutionException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ContainerExecutionException(Throwable cause)
    {
        super(cause);
    }
}
