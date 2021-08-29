package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.containerexecutor.FileTrees.NoRootException;
import au.com.helixta.adl.gradle.containerexecutor.PreparedCommandLine.Argument;
import au.com.helixta.adl.gradle.containerexecutor.PreparedCommandLine.FileType;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.DistributionService;
import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;
import au.com.helixta.adl.gradle.generator.AdlToolLogger;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import au.com.helixta.adl.gradle.generator.LineProcessingOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileTree;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class NativeExecutor implements ContainerExecutor
{
    private final DistributionService distributionService;
    private final DistributionSpecifier distributionSpecifier;
    private final ExecutableResolver executableResolver;
    private final ExecOperations execOperations;
    private final ArchiveProcessor archiveProcessor;
    private final AdlToolLogger adlLog;
    private final String logToolName;

    /**
     * Creates a native executor.
     *
     * @param distributionService distribution service used to download and install the tool.
     * @param distributionSpecifier specifier to specify version and architecture of the tool to run.
     * @param executableResolver resolves the executable to run in the distribution.
     * @param execOperations operations object for running processes.
     * @param archiveProcessor used for turning archives into directories.
     * @param adlLog logger.
     * @param logToolName name of the tool to use when logging.
     */
    public NativeExecutor(DistributionService distributionService, DistributionSpecifier distributionSpecifier,
                          ExecutableResolver executableResolver, ExecOperations execOperations,
                          ArchiveProcessor archiveProcessor, AdlToolLogger adlLog, String logToolName)
    {
        this.distributionService = Objects.requireNonNull(distributionService);
        this.distributionSpecifier = Objects.requireNonNull(distributionSpecifier);
        this.executableResolver = Objects.requireNonNull(executableResolver);
        this.execOperations = Objects.requireNonNull(execOperations);
        this.archiveProcessor = Objects.requireNonNull(archiveProcessor);
        this.adlLog = Objects.requireNonNull(adlLog);
        this.logToolName = Objects.requireNonNull(logToolName);
    }

    @Override
    public void execute(PreparedCommandLine commandLine)
    throws IOException, DistributionNotFoundException, ContainerExecutionException
    {
        //Prepare and install distribution
        File toolBaseDirectory = distributionService.resolveDistribution(distributionSpecifier);
        String executable = executableResolver.resolveExecutable(toolBaseDirectory.getAbsolutePath(), distributionSpecifier);

        List<String> args = createNativeCommandLine(commandLine);

        List<String> infos = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        try (LineProcessingOutputStream adlOut = new LineProcessingOutputStream(Charset.defaultCharset(),
                                                                                line -> { adlLog.info(logToolName, line); infos.add(line); });
             LineProcessingOutputStream adlErr = new LineProcessingOutputStream(Charset.defaultCharset(),
                                                                                line -> { adlLog.error(logToolName, line); errors.add(line); }))
        {
            ExecResult result = execOperations.exec(e ->
                                {
                                    e.setExecutable(executable);
                                    //e.setWorkingDir(); //TODO
                                    e.setArgs(args);
                                    e.setStandardOutput(adlOut);
                                    e.setErrorOutput(adlErr);
                                });

            //Normally this gets called anyway from execOperations.exec() but spec doesn't say it should so we'll do it here just in case
            result.assertNormalExitValue();
        }
        catch (GradleException e) //Actually org.gradle.process.internal.ExecException but it's internal so we shouldn't use it
        {
            //Occurs when process execution fails

            //Certain versions of adlc log everything to stdout - in the case of errors send this all to adlLog.error too
            //if infos weren't actually being logged
            //This is so the user doesn't just see an error that adlc failed without any reason/cause from the process
            if (errors.isEmpty() && !infos.isEmpty() && !adlLog.isInfoEnabled())
            {
                for (String info : infos)
                {
                    adlLog.error(logToolName, info);
                }
            }

            //Execution failed
            throw new ContainerExecutionException(e);
        }
    }

    private List<String> createNativeCommandLine(PreparedCommandLine commandLine)
    throws IOException
    {
        List<String> nativeCommandLine = new ArrayList<>();
        for (Argument argument : commandLine.getArguments())
        {
            nativeCommandLine.addAll(argumentToStrings(argument));
        }
        return nativeCommandLine;
    }

    private List<String> argumentToStrings(PreparedCommandLine.Argument argument)
    throws IOException
    {
        if (argument instanceof PreparedCommandLine.StringArgument)
            return Collections.singletonList(((PreparedCommandLine.StringArgument)argument).getArgument());
        else if (argument instanceof PreparedCommandLine.ContainerFile)
        {
            PreparedCommandLine.ContainerFile containerFile = (PreparedCommandLine.ContainerFile)argument;

            //If we have a normal file but file type is directory, assume an archive and process it
            //Gradle will expand to a local directory the first time it is traversed, and this is all managed
            //by Gradle
            File hostDirectory;
            if (containerFile.getHostFile().isFile() && containerFile.getFileType() == FileType.DIRECTORY)
            {
                FileTree archiveFileTree = archiveProcessor.archiveToFileTree(containerFile.getHostFile());

                //Need to visit every file in the archive tree to ensure it is extracted to output directory
                archiveFileTree.visit(fileVisitDetails -> {});

                //Pull out the expanded root dir of the archive
                try
                {
                    hostDirectory = FileTrees.fileTreeRootDirectory(archiveFileTree);
                }
                catch (NoRootException e)
                {
                    //Might happen if the archive is empty, in which case just omit the argument
                    //and bail out now
                    return Collections.emptyList();
                }
            }
            else
                hostDirectory = containerFile.getHostFile();

            //We now have a directory on the host, turn it into an argument
            String stringArgument = containerFile.getCommandLineGenerator().generate(hostDirectory.getAbsolutePath());

            return Collections.singletonList(stringArgument);
        }
        else if (argument instanceof PreparedCommandLine.ContainerFileTree)
        {
            List<String> generatedArgs = new ArrayList<>(2);
            PreparedCommandLine.ContainerFileTree tree = (PreparedCommandLine.ContainerFileTree)argument;
            generatedArgs.addAll(tree.getCommandLineGenerator().generateFromTree(tree.getHostFileTree(), FileTrees.fileTreeRoots(tree.getHostFileTree()).stream().map(File::getAbsolutePath).collect(Collectors.toList())));
            tree.getHostFileTree().visit(fileVisitDetails -> generatedArgs.addAll(tree.getCommandLineGenerator().generateFromTreeElement(tree.getHostFileTree(), fileVisitDetails, fileVisitDetails.getFile().getAbsolutePath())));
            return generatedArgs;
        }
        else
            throw new Error("Unknown argument type: " + argument.getClass().getName());
    }
}
