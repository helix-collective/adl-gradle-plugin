package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.distribution.AdlDistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;
import au.com.helixta.adl.gradle.distribution.AdlDistributionSpec;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class NativeAdlGenerator implements AdlGenerator
{
    private final AdlToolLogger adlLog;
    private final ExecOperations execOperations;
    private final AdlDistributionService adlDistributionService;
    private final AdlcCommandLineGenerator adlcCommandProcessor = new AdlcCommandLineGenerator();
    private final FileSystemMapper fileSystemMapper = new LocalFileSystemMapper();

    public NativeAdlGenerator(ExecOperations execOperations, AdlDistributionService adlDistributionService, AdlToolLogger adlLog)
    {
        this.execOperations = Objects.requireNonNull(execOperations);
        this.adlDistributionService = Objects.requireNonNull(adlDistributionService);
        this.adlLog = Objects.requireNonNull(adlLog);
    }

    protected AdlDistributionSpec adlDistributionSpecForConfiguration(AdlConfiguration configuration)
    {
        //TODO unhardcode
        return new AdlDistributionSpec("0.14", "amd64", "linux");
    }

    @Override
    public void generate(AdlConfiguration configuration, Iterable<? extends GenerationConfiguration> generations)
    throws AdlGenerationException
    {
        try
        {
            File adlBinaryDir = adlDistributionService.adlDistribution(adlDistributionSpecForConfiguration(configuration));
            File adlExecutable = new File(new File(adlBinaryDir, "bin"), "adlc"); //TODO windows suffix, etc.

            for (GenerationConfiguration generation : generations)
            {
                List<String> args = adlcCommandProcessor.createAdlcCommand(configuration, generation, fileSystemMapper);

                ExecResult result = execOperations.exec(e ->
                {
                   e.setExecutable(adlExecutable.getAbsolutePath());
                   //e.setWorkingDir();
                   e.setArgs(args);
                   //e.setStandardOutput() TODO logging
                    //e.setErrorOutput()
                });
                result.assertNormalExitValue(); //TODO better
            }
        }
        catch (IOException | AdlDistributionNotFoundException e)
        {
            throw new AdlGenerationException(e);
        }
    }

    @Override
    public void close() throws IOException
    {
        //Nothing to do here
    }
}
