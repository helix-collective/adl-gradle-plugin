package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Executable resolver that resolves an executable using the same path regardless of the platform and adding a platform specific extension
 * if needed, such as '.exe' for Windows.
 */
public class SimpleExecutableResolver implements ExecutableResolver
{
    private final String executablePath;

    /**
     * Creates an executable resolver with a platform-neutral path for the executable.
     *
     * @param executablePath path to the executable relative to the distribution base directory, with '/' for separators and no platform-specific executable extension in the
     *                       file name.
     */
    public SimpleExecutableResolver(String executablePath)
    {
        this.executablePath = Objects.requireNonNull(executablePath);
    }

    @Override
    public String resolveExecutable(String distributionBaseDirectory, DistributionSpecifier distributionSpecifier)
    throws IOException
    {
        String path = FilenameUtils.concat(distributionBaseDirectory, executablePath);
        if (distributionSpecifier.getOs().isWindows())
            path = FilenameUtils.separatorsToWindows(path) + ".exe";
        else
            path = FilenameUtils.separatorsToUnix(path);

        return path;
    }
}
