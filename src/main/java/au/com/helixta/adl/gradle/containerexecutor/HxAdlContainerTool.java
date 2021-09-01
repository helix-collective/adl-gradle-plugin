package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.SqlSchemaGenerationConfiguration;
import au.com.helixta.adl.gradle.distribution.HxAdlDistributionService;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HxAdlContainerTool extends ContainerTool<HxAdlContainerTool.AdlFullConfiguration>
{
    public HxAdlContainerTool(Environment environment)
    {
        super(adlStaticToolConfiguration(environment), environment);
    }

    private static StaticToolConfiguration adlStaticToolConfiguration(Environment environment)
    {
        HxAdlDistributionService distributionService = new HxAdlDistributionService(environment.getHomeDirProvider(), environment.getFileSystemOperations(),
                                                                                    environment.getArchiveOperations(), environment.getProject());

        return new StaticToolConfiguration(distributionService, new SimpleExecutableResolver("bin/hx-adl"), "hx-adl", "/opt/hx-adl", "/data",
                                           HxAdlContainerTool::configureDockerfile,
                                           "hxadl/hxadl", "hx-adl-gradle", HxAdlContainerTool::workaroundBadShellScriptArgumentTransform);
    }

    /**
     * hx-adl is a shell script but for some unknown reason it starts with a blank line instead of a shebang.  Works fine on native platforms but not in Docker, so we need
     * to transform the command line to specifically use the bash shell to invoke this one.
     */
    private static List<String> workaroundBadShellScriptArgumentTransform(List<String> args)
    {
        List<String> newArgs = new ArrayList<>(args.size() + 1);
        newArgs.add("/bin/bash");
        newArgs.addAll(args);
        return newArgs;
    }

    private static void configureDockerfile(DockerImageDefinition definition)
    {
        List<String> preRunCommands = ImmutableList.of(
                "apt-get update",
                "DEBIAN_FRONTEND=noninteractive apt-get -y install nodejs npm",
                "npm install --global yarn",
                "rm -rf /var/lib/apt/lists/* /var/cache/apt/*"
        );
        String singlePreRunCommand = String.join(" && ", preRunCommands);

        //Not strictly necessary but want to do this once and save into image instead of having to download every run
        String postRunCommand = "yarn install --cwd /opt/hx-adl/lib/js";

        definition.getCommands().add(0, "RUN " + singlePreRunCommand);
        definition.getCommands().add("RUN " + postRunCommand);

        //TODO
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
        hxAdlCommand(config.getAdl(), config.getGeneration(), commandLine);
        return commandLine;
    }

    private void hxAdlCommand(AdlConfiguration adlConfiguration, SqlSchemaGenerationConfiguration generation, PreparedCommandLine commandLine)
    {
        commandLine.argument("sql");
        commandLine.argument("--outputdir");
        commandLine.argument(generation.getOutputDirectory().get(), "adloutput", PreparedCommandLine.FileTransferMode.OUTPUT);

        String searchDirBaseName = "adlsearchdir";
        int searchDirCounter = 1;
        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String searchDirLabel = searchDirBaseName + searchDirCounter;
            commandLine.argument("--searchdir");
            commandLine.argument(searchDir, searchDirLabel, PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY);
            searchDirCounter++;
        }

        commandLine.argument(adlConfiguration.getSource(), "sources");
    }

    public static class AdlFullConfiguration
    {
        private final AdlConfiguration adl;
        private final SqlSchemaGenerationConfiguration generation;
        private final DockerConfiguration docker;

        public AdlFullConfiguration(AdlConfiguration adl, SqlSchemaGenerationConfiguration generation, DockerConfiguration docker)
        {
            this.adl = adl;
            this.generation = generation;
            this.docker = docker;
        }

        public AdlConfiguration getAdl()
        {
            return adl;
        }

        public SqlSchemaGenerationConfiguration getGeneration()
        {
            return generation;
        }

        public DockerConfiguration getDocker()
        {
            return docker;
        }
    }
}
