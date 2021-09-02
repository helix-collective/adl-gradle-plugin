
package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.DockerConfiguration;
import au.com.helixta.adl.gradle.config.SqlSchemaGenerationConfiguration;
import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.generator.AdlToolLogger;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import au.com.helixta.adl.gradle.generator.SimpleAdlToolLogger;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.google.common.io.Resources;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.process.ExecOperations;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TestHxAdlContainerTool
{
    private static DockerClientFactory dockerFactory;
    private static ObjectFactory objectFactory;
    private static ArchiveProcessor archiveProcessor;
    private static ExecOperations execOperations;
    private static Logger gradleLogger;
    private static TargetMachineFactory targetMachineFactory;
    private static ArchiveOperations archiveOperations;
    private static GradleUserHomeDirProvider gradleUserHomeDirProvider;
    private static FileSystemOperations fileSystemOperations;
    private static Project project;

    @BeforeAll
    private static void setUpDocker()
    {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        dockerFactory = new DockerClientFactory(config);
    }

    @BeforeAll
    private static void setUpGradleEnvironment(@TempDir File tempDir)
    {
        Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
        objectFactory = p.getObjects();
        InjectReceiver injectReceiver = objectFactory.newInstance(InjectReceiver.class);
        archiveProcessor = new ArchiveProcessor(injectReceiver.archiveOperations);
        execOperations = injectReceiver.execOperations;
        gradleLogger = p.getLogger();
        targetMachineFactory = injectReceiver.targetMachineFactory;
        archiveOperations = injectReceiver.archiveOperations;
        gradleUserHomeDirProvider = injectReceiver.gradleUserHomeDirProvider;
        fileSystemOperations = injectReceiver.fileSystemOperations;
        project = p;
    }

    /**
     * Purely for exercising Gradle's injection system to get objects.
     */
    public static class InjectReceiver
    {
        public final ArchiveOperations archiveOperations;
        public final ExecOperations execOperations;
        public final TargetMachineFactory targetMachineFactory;
        public final GradleUserHomeDirProvider gradleUserHomeDirProvider;
        public final FileSystemOperations fileSystemOperations;

        @Inject
        public InjectReceiver(ArchiveOperations archiveOperations, ExecOperations execOperations, TargetMachineFactory targetMachineFactory, GradleUserHomeDirProvider gradleUserHomeDirProvider,
                              FileSystemOperations fileSystemOperations)
        {
            this.archiveOperations = archiveOperations;
            this.execOperations = execOperations;
            this.targetMachineFactory = targetMachineFactory;
            this.gradleUserHomeDirProvider = gradleUserHomeDirProvider;
            this.fileSystemOperations = fileSystemOperations;
        }
    }

    /**
     * Verify that the ADL tool can be invoked on a very simple use case and can read and produce files.
     */
    @Test
    void test() throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        //Filesystem setup
        Path adlOut = Files.createTempDirectory(project.getProjectDir().toPath(), "adlout");
        Path sources = Files.createTempDirectory(project.getProjectDir().toPath(), "adlsources");

        //Set up search path
        Path searchPath = Files.createTempDirectory(project.getProjectDir().toPath(), "adlsearch");
        Path searchPathCommon = searchPath.resolve("common");
        Files.createDirectories(searchPathCommon);
        URL dbAdlResource = Resources.getResource(TestHxAdlContainerTool.class, "/db.adl");
        Path dbAdlFile = searchPathCommon.resolve("db.adl");
        try (InputStream is = dbAdlResource.openStream())
        {
            Files.copy(is, dbAdlFile);
        }
        Path adlFile = Files.createFile(sources.resolve("cat.adl"));
        Files.write(adlFile,
                ("module sub {\n" +
                "import common.db.DbTable;\n" +
                "struct Cat {\n" +
                "    String name;\n" +
                "    Int32 age;\n" +
                "};\n" +
                 "annotation Cat DbTable {\n" +
                 "   \"withIdPrimaryKey\" : true\n" +
                 "};\n" +
                "};\n").getBytes(StandardCharsets.UTF_8)
        );

        //Tool setup
        AdlToolLogger toolLog = new ConsoleAdlToolLogger();
        ContainerTool.Environment env = new ContainerTool.Environment(execOperations, toolLog, dockerFactory, targetMachineFactory, objectFactory, archiveOperations, archiveProcessor, gradleUserHomeDirProvider, fileSystemOperations, project, gradleLogger);
        HxAdlContainerTool tool = new HxAdlContainerTool(env);

        AdlConfiguration adl = new AdlConfiguration()
        {
            @Override
            public FileTree getSource()
            {
                //Our source directory
                return objectFactory.fileTree().setDir(sources);
            }

            @Override
            public FileCollection getSearchDirectories()
            {
                //Just empty, no search directories
                return objectFactory.fileCollection().from(searchPath);
            }

            @Override
            public boolean isVerbose()
            {
                return true;
            }

            @Override
            public String getVersion()
            {
                return "1.0.5";
            }
        };
        DockerConfiguration dockerConfig = objectFactory.newInstance(DockerConfiguration.class);
        SqlSchemaGenerationConfiguration generation = objectFactory.newInstance(SqlSchemaGenerationConfiguration.class);
        generation.setOutputDirectory(adlOut.toFile());

        HxAdlContainerTool.AdlFullConfiguration config = new HxAdlContainerTool.AdlFullConfiguration(adl, generation, dockerConfig);

        //Run the tool - just use Docker because native installation is messier and won't work on all platforms
        tool.execute(config, ExecutionPlatform.DOCKER);

        //Verify the results - check Java file was written has a bit of known content in it
        Path createSqlFile = adlOut.resolve("create.sql");
        assertThat(createSqlFile).isRegularFile();
        assertThat(new String(Files.readAllBytes(createSqlFile), StandardCharsets.UTF_8)).contains("create table cat");
    }
}
