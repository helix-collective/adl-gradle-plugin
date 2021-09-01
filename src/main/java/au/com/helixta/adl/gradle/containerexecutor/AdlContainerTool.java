package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import au.com.helixta.adl.gradle.config.JavascriptGenerationConfiguration;
import au.com.helixta.adl.gradle.config.TypescriptGenerationConfiguration;
import au.com.helixta.adl.gradle.distribution.AdlDistributionService;

import java.io.File;
import java.util.function.UnaryOperator;

public class AdlContainerTool extends ContainerTool<AdlContainerTool.AdlFullConfiguration>
{
    public AdlContainerTool(Environment environment)
    {
        super(adlStaticToolConfiguration(environment), environment);
    }

    private static StaticToolConfiguration adlStaticToolConfiguration(Environment environment)
    {
        AdlDistributionService distributionService = new AdlDistributionService(environment.getHomeDirProvider(), environment.getFileSystemOperations(),
                                                                                environment.getArchiveOperations(), environment.getProject());

        return new StaticToolConfiguration(distributionService, new SimpleExecutableResolver("bin/adlc"), "adlc", "/opt/adl", "/data",
                                           DockerImageDefinitionTransformer.NO_MODIFICATION, "adl/adlc", "adl-gradle", UnaryOperator.identity());
    }

    @Override
    protected String readDistributionVersion(AdlFullConfiguration config)
    {
        return config.getAdl().getVersion();
    }

    @Override
    protected DockerConfiguration readDockerConfiguration(AdlFullConfiguration config)
    {
        return config.getDocker();
    }

    @Override
    protected PreparedCommandLine createCommandLine(AdlFullConfiguration config)
    {
        PreparedCommandLine commandLine = new PreparedCommandLine();
        adlcCommand(config.getAdl(), config.getGeneration(), commandLine);
        return commandLine;
    }

    private void adlcCommand(AdlConfiguration adlConfiguration, GenerationConfiguration generation, PreparedCommandLine commandLine)
    {
        if (generation instanceof JavaGenerationConfiguration)
            adlcJavaCommand(adlConfiguration, (JavaGenerationConfiguration)generation, commandLine);
        else if (generation instanceof TypescriptGenerationConfiguration)
            adlcTypescriptCommand(adlConfiguration, (TypescriptGenerationConfiguration)generation, commandLine);
        else if (generation instanceof JavascriptGenerationConfiguration)
            adlcJavascriptCommand(adlConfiguration, (JavascriptGenerationConfiguration)generation, commandLine);
        else //Should not happen as we're covering all known subtypes
            throw new Error("Unknown generation type: " + generation.getClass().getName());
    }

    /**
     * Generate the adlc command line arguments to generate Java files for the specified Java generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Java generation configuration to generate files for.
     * @param commandLine the command line to add arguments to.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/backend-java.md">ADL Java Backend</a>
     */
    private void adlcJavaCommand(AdlConfiguration adlConfiguration, JavaGenerationConfiguration generation, PreparedCommandLine commandLine)
    {
        commandLine.argument("java");
        commandLine.argument(generation.getOutputDirectory().get(), "adloutput", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--outputdir=" + containerPath);

        String searchDirBaseName = "adlsearchdir";
        int searchDirCounter = 1;
        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String searchDirLabel = searchDirBaseName + searchDirCounter;
            commandLine.argument(searchDir, searchDirLabel, PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY, containerPath -> "--searchdir=" + containerPath);
            searchDirCounter++;
        }

        if (generation.getJavaPackage() != null && !generation.getJavaPackage().trim().isEmpty())
            commandLine.argument("--package=" + generation.getJavaPackage());

        if (adlConfiguration.isVerbose())
            commandLine.argument("--verbose");

        if (generation.isGenerateAdlRuntime())
            commandLine.argument("--include-rt");
        if (generation.getAdlRuntimePackage() != null && !generation.getAdlRuntimePackage().isEmpty())
            commandLine.argument("--rtpackage=" + generation.getAdlRuntimePackage());

        if (generation.isGenerateTransitive())
            commandLine.argument("--generate-transitive");
        if (generation.getSuppressWarningsAnnotation() != null && !generation.getSuppressWarningsAnnotation().isEmpty())
            commandLine.argument("--suppress-warnings-annotation=" + generation.getSuppressWarningsAnnotation());
        if (generation.getHeaderComment() != null && !generation.getHeaderComment().isEmpty())
            commandLine.argument("--header-comment=" + generation.getHeaderComment());

        if (generation.getManifest().isPresent())
            commandLine.argument(generation.getManifest().get(), "manifest", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--manifest=" + containerPath);

        generation.getCompilerArgs().forEach(commandLine::argument);

        commandLine.argument(adlConfiguration.getSource(), "sources");
    }

    /**
     * Generate the adlc command line arguments to generate Typescript files for the specified Typescript generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Typescript generation configuration to generate files for.
     * @param commandLine the command line to add arguments to.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/backend-java.md">ADL Java Backend</a>
     */
    private void adlcTypescriptCommand(AdlConfiguration adlConfiguration, TypescriptGenerationConfiguration generation, PreparedCommandLine commandLine)
    {
        commandLine.argument("typescript");
        commandLine.argument(generation.getOutputDirectory().get(), "adloutput", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--outputdir=" + containerPath);

        String searchDirBaseName = "adlsearchdir";
        int searchDirCounter = 1;
        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String searchDirLabel = searchDirBaseName + searchDirCounter;
            commandLine.argument(searchDir, searchDirLabel, PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY, containerPath -> "--searchdir=" + containerPath);
            searchDirCounter++;
        }

        if (adlConfiguration.isVerbose())
            commandLine.argument("--verbose");

        if (generation.isGenerateTransitive())
            commandLine.argument("--generate-transitive");
        if (generation.isGenerateResolver())
            commandLine.argument("--include-resolver");
        if (!generation.isGenerateAst())
            commandLine.argument("--exclude-ast");

        if (generation.isGenerateAdlRuntime())
            commandLine.argument("--include-rt");
        if (generation.getRuntimeModuleName() != null)
            commandLine.argument("--runtime-dir=" + generation.getRuntimeModuleName());

        if (generation.getManifest().isPresent())
            commandLine.argument(generation.getManifest().get(), "manifest", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--manifest=" + containerPath);

        generation.getCompilerArgs().forEach(commandLine::argument);

        commandLine.argument(adlConfiguration.getSource(), "sources");
    }

    /**
     * Generate the adlc command line arguments to generate Typescript files for the specified Typescript generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Javascript generation configuration to generate files for.
     * @param commandLine the command line to add arguments to.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/backend-java.md">ADL Java Backend</a>
     */
    private void adlcJavascriptCommand(AdlConfiguration adlConfiguration, JavascriptGenerationConfiguration generation, PreparedCommandLine commandLine)
    {
        commandLine.argument("javascript");
        commandLine.argument(generation.getOutputDirectory().get(), "adloutput", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--outputdir=" + containerPath);

        String searchDirBaseName = "adlsearchdir";
        int searchDirCounter = 1;
        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String searchDirLabel = searchDirBaseName + searchDirCounter;
            commandLine.argument(searchDir, searchDirLabel, PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY, containerPath -> "--searchdir=" + containerPath);
            searchDirCounter++;
        }

        if (adlConfiguration.isVerbose())
            commandLine.argument("--verbose");

        if (generation.getManifest().isPresent())
            commandLine.argument(generation.getManifest().get(), "manifest", PreparedCommandLine.FileTransferMode.OUTPUT, containerPath -> "--manifest=" + containerPath);

        generation.getCompilerArgs().forEach(commandLine::argument);

        commandLine.argument(adlConfiguration.getSource(), "sources");
    }

    public static class AdlFullConfiguration
    {
        private final AdlConfiguration adl;
        private final GenerationConfiguration generation;
        private final DockerConfiguration docker;

        public AdlFullConfiguration(AdlConfiguration adl, GenerationConfiguration generation, DockerConfiguration docker)
        {
            this.adl = adl;
            this.generation = generation;
            this.docker = docker;
        }

        public AdlConfiguration getAdl()
        {
            return adl;
        }

        public GenerationConfiguration getGeneration()
        {
            return generation;
        }

        public DockerConfiguration getDocker()
        {
            return docker;
        }
    }
}
