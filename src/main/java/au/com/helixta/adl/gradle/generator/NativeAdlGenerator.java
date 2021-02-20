package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.distribution.AdlDistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;
import au.com.helixta.adl.gradle.distribution.AdlDistributionSpec;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NativeAdlGenerator implements AdlGenerator
{
    private static final String TOOL_ADLC = "adlc";

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

                List<String> infos = Collections.synchronizedList(new ArrayList<>());
                List<String> errors = Collections.synchronizedList(new ArrayList<>());

                try (LineProcessingOutputStream adlOut = new LineProcessingOutputStream(Charset.defaultCharset(),
                                                                line -> { adlLog.info(TOOL_ADLC, line); infos.add(line); });
                     LineProcessingOutputStream adlErr = new LineProcessingOutputStream(Charset.defaultCharset(),
                                                                line -> { adlLog.error(TOOL_ADLC, line); errors.add(line); }))
                {
                    execOperations.exec(e ->
                    {
                        e.setExecutable(adlExecutable.getAbsolutePath());
                        //e.setWorkingDir();
                        e.setArgs(args);
                        e.setStandardOutput(adlOut);
                        e.setErrorOutput(adlErr);
                    });
                }
                catch (GradleException e) //Actually org.gradle.process.internal.ExecException but it's internal so we shouldn't use it
                {
                    //Occurs when process execution fails

                    //Certain versions of adlc log everything to stdout - in the case of errors send this all to adlLog.error too
                    if (errors.isEmpty() && !infos.isEmpty())
                    {
                        for (String info : infos)
                        {
                            adlLog.error(TOOL_ADLC, info);
                        }
                    }

                    //Rethrow original exception again after error log processing
                    throw e;
                }
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
