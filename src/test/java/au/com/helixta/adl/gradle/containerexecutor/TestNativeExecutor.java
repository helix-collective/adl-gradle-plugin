package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.distribution.DistributionNotFoundException;
import au.com.helixta.adl.gradle.distribution.DistributionService;
import au.com.helixta.adl.gradle.distribution.DistributionSpecifier;
import au.com.helixta.adl.gradle.generator.AdlToolLogger;
import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.internal.DefaultExecSpec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestNativeExecutor
{
    @Mock
    private DistributionService distributionService;

    @Mock
    private ExecOperations execOperations;

    @Mock
    private AdlToolLogger adlLog;

    @Mock
    private ExecResult execResult;

    @TempDir
    Path tempDir;

    private static ObjectFactory objectFactory;

    private static final DistributionSpecifier distributionSpecifier = new DistributionSpecifier("1.0", Architectures.of(Architectures.X86_64), new DefaultOperatingSystem("linux"));

    private final List<ExecSpec> invokedSpecs = new ArrayList<>();

    @BeforeAll
    private static void setUpGradleEnvironment(@TempDir File tempDir)
    {
        Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
        objectFactory = p.getObjects();
    }

    @BeforeEach
    private void setUp()
    throws IOException, DistributionNotFoundException
    {
        invokedSpecs.clear();

        when(distributionService.resolveDistribution(any())).thenReturn(tempDir.toFile());
        when(execOperations.exec(any())).then(invocation ->
        {
            @SuppressWarnings("unchecked") Action<ExecSpec> action = invocation.getArgument(0, Action.class);
            ExecSpec spec = new DefaultExecSpec(new IdentityFileResolver());
            action.execute(spec);
            invokedSpecs.add(spec);
            return execResult;
        });
    }

    /**
     * Checks that a single exec spec was invoked and returns it.
     */
    private ExecSpec invokedExecSpec()
    {
        assertThat(invokedSpecs).hasSize(1);
        return invokedSpecs.get(0);
    }

    @Test
    void singleStringArgument()
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        NativeExecutor executor = new NativeExecutor(distributionService, distributionSpecifier, new SimpleExecutableResolver("myprogram"), execOperations, adlLog, "LOG");

        PreparedCommandLine c = new PreparedCommandLine().argument("-test");
        executor.execute(c);

        assertThat(invokedExecSpec().getExecutable()).endsWith("myprogram");
        assertThat(invokedExecSpec().getArgs()).containsExactly("-test");
    }

    @Test
    void directoryArgument()
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        NativeExecutor executor = new NativeExecutor(distributionService, distributionSpecifier, new SimpleExecutableResolver("myprogram"), execOperations, adlLog, "LOG");

        PreparedCommandLine c = new PreparedCommandLine().argument(tempDir.toFile(), "unusedLabel", PreparedCommandLine.FileTransferMode.INPUT, PreparedCommandLine.FileType.DIRECTORY);
        executor.execute(c);

        assertThat(invokedExecSpec().getExecutable()).endsWith("myprogram");
        assertThat(invokedExecSpec().getArgs()).containsExactly(tempDir.toAbsolutePath().toString());
    }

    @Test
    void fileTreeArgumentMultipleFilesExpanded()
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        Path argDir = Files.createTempDirectory(tempDir, "arg");
        Path argFile1 = Files.createFile(argDir.resolve("file1.txt"));
        Path argFile2 = Files.createFile(argDir.resolve("file2.txt"));

        NativeExecutor executor = new NativeExecutor(distributionService, distributionSpecifier, new SimpleExecutableResolver("myprogram"), execOperations, adlLog, "LOG");

        FileTree tree = objectFactory.fileTree().from(argDir);
        PreparedCommandLine c = new PreparedCommandLine().argument(tree, "unusedLabel");
        executor.execute(c);

        assertThat(invokedExecSpec().getExecutable()).endsWith("myprogram");
        assertThat(invokedExecSpec().getArgs()).containsExactly(argFile1.toAbsolutePath().toString(), argFile2.toAbsolutePath().toString());
    }

    @Test
    void fileTreeArgumentBaseDirectory()
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        Path argDir = Files.createTempDirectory(tempDir, "arg");
        Files.createFile(argDir.resolve("file1.txt"));
        Files.createFile(argDir.resolve("file2.txt"));

        NativeExecutor executor = new NativeExecutor(distributionService, distributionSpecifier, new SimpleExecutableResolver("myprogram"), execOperations, adlLog, "LOG");

        FileTree tree = objectFactory.fileTree().from(argDir);
        PreparedCommandLine c = new PreparedCommandLine().argument(tree, "unusedLabel", new PreparedCommandLine.SingleBaseDirectoryCommandLineGenerator());
        executor.execute(c);

        assertThat(invokedExecSpec().getExecutable()).endsWith("myprogram");
        assertThat(invokedExecSpec().getArgs()).containsExactly(argDir.toAbsolutePath().toString());
    }

    @Test
    void fileTreeArgumentFromMultiSource()
    throws ContainerExecutionException, IOException, DistributionNotFoundException
    {
        Path argDir1 = Files.createTempDirectory(tempDir, "arg");
        Path argFile1 = Files.createFile(argDir1.resolve("file1.txt"));
        Path argFile2 = Files.createFile(argDir1.resolve("file2.txt"));
        Path argDir2 = Files.createTempDirectory(tempDir, "arg2");
        Path argDir2Sub = argDir2.resolve("sub");
        Files.createDirectories(argDir2Sub);
        Path argFile3 = Files.createFile(argDir2Sub.resolve("file3.txt"));

        NativeExecutor executor = new NativeExecutor(distributionService, distributionSpecifier, new SimpleExecutableResolver("myprogram"), execOperations, adlLog, "LOG");

        FileTree tree = objectFactory.fileTree().from(argDir1).plus(objectFactory.fileTree().from(argDir2));
        PreparedCommandLine c = new PreparedCommandLine().argument(tree, "unusedLabel");
        executor.execute(c);

        assertThat(invokedExecSpec().getExecutable()).endsWith("myprogram");
        assertThat(invokedExecSpec().getArgs()).containsExactly(argFile1.toAbsolutePath().toString(), argFile2.toAbsolutePath().toString(), argFile3.toAbsolutePath().toString());
    }

    //TODO archive processing
}
