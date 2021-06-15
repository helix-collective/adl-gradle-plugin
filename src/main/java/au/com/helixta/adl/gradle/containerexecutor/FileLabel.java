package au.com.helixta.adl.gradle.containerexecutor;

/**
 * Interface for labelling a file or directory that is used as an argument for a container executor.
 */
public interface FileLabel
{
    /**
     * @return a label for the file or directory.
     */
    public String getLabel();
}
