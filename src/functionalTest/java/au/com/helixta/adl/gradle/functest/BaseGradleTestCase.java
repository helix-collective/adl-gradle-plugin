package au.com.helixta.adl.gradle.functest;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Base test class for running a set of integration test Gradle projects with a particular configuration.
 * Subclasses will apply additional configuration through overridden methods to run tests in a specific way.
 */
abstract class BaseGradleTestCase
{
    /**
     * Temporary directory used for making workspace directories when not explicitly configured.
     */
    @TempDir
    Path tempDir;

    /**
     * Base workspace directory where tests will be extracted under.
     */
    private Path projectWorkspace;

    /**
     * Directory to use for testkit's Gradle home directory when running tests.  May not be set, in which case testkit will use its default.
     */
    private Path testKitDirectory;

    /**
     * By setting this system property, a user will be able to run tests with specific names only by using
     * a RegExp pattern.
     */
    private static final Pattern testFilter = testFilterToPattern(System.getProperty("gradletest"));

    /**
     * Turns a test filter into a pattern suitable for filtering test names.
     *
     * @param propertyValue the test filter property value.
     *
     * @return the pattern, or null if no filtering should be applied.
     */
    private static Pattern testFilterToPattern(String propertyValue)
    {
        if (propertyValue == null)
            return null;

        propertyValue = propertyValue.trim();
        if (propertyValue.isEmpty())
            return null;

        return Pattern.compile(propertyValue, Pattern.CASE_INSENSITIVE);
    }

    @BeforeEach
    private void setup()
    {
        //When run from Gradle, directory will be passed in which will be underneath the build
        //directory somewhere
        //When run from elsewhere and the property has not been set, just use a temporary directory
        //which gets blown away at the end
        String workspaceProperty = System.getProperty("test.projectworkspace.directory");
        if (workspaceProperty != null)
            workspaceProperty = workspaceProperty.trim();
        if (workspaceProperty != null && workspaceProperty.isEmpty())
            workspaceProperty = null;
        if (workspaceProperty != null)
            projectWorkspace = Paths.get(workspaceProperty);
        else
            projectWorkspace = tempDir;

        //Used for Gradle home when running testkit tests
        String testKitDirectoryProperty = System.getProperty("test.testkit.directory");
        if (testKitDirectoryProperty != null)
            testKitDirectoryProperty = testKitDirectoryProperty.trim();
        if (testKitDirectoryProperty != null && testKitDirectoryProperty.isEmpty())
            testKitDirectoryProperty = null;
        if (testKitDirectoryProperty != null)
            testKitDirectory = Paths.get(testKitDirectoryProperty);
    }

    /**
     * Returns the test workspace to use for extracting test projects to.
     * Each test type will have an appropriately named workspace directory.
     *
     * @param projectWorkspace the base project workspace to create test workspaces under.
     *
     * @return workspace directory for the current test type.
     */
    protected abstract Path getTestWorkspace(Path projectWorkspace);

    /**
     * Generates tests, one for each gradle project found underneath the 'gradleTest' base directory
     * on the classpath.
     *
     * @return a set of tests.
     *
     * @throws IOException if an error occurs.
     */
    @TestFactory
    List<DynamicTest> adlTestCases()
    throws IOException
    {
        Path testWorkspace = getTestWorkspace(projectWorkspace);
        Files.createDirectories(testWorkspace);
        System.out.println("Test workspace: " + testWorkspace.toRealPath());

        List<DynamicTest> tests = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph().acceptPaths("gradleTest/*")
                                                     .scan())
        {
            List<Resource> items = new ArrayList<>();
            items.addAll(scanResult.getResourcesWithLeafName("build.gradle.kts"));
            items.addAll(scanResult.getResourcesWithLeafName("build.gradle"));
            for (Resource item : items)
            {
                String buildGradlePath = item.getPathRelativeToClasspathElement();
                //System.out.println(buildGradlePath);
                String parentPath = parentPath(buildGradlePath);

                //Copy resources to target project dir
                for (Resource child : scanResult.getResourcesMatchingPattern(Pattern.compile("^" + Pattern.quote(parentPath) + ".*")))
                {
                    //System.out.println(" > " + child + " (" + child.getPathRelativeToClasspathElement() + ")");
                    Path targetFile = testWorkspace.resolve(child.getPathRelativeToClasspathElement());
                    Files.createDirectories(targetFile.getParent());
                    try (InputStream is = child.open())
                    {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                //Set up and create the tests
                Path gradleFile = testWorkspace.resolve(buildGradlePath);

                Executable ex = () ->
                {
                    checkAssumptions(gradleFile);
                    System.out.println("Running " + buildGradlePath);
                    GradleRunner runner = GradleRunner.create()
                                                      //.withDebug(true)
                                                      .withProjectDir(gradleFile.getParent().toFile())
                                                      .withArguments(getTestArguments())
                                                      .withPluginClasspath()
                                                      .forwardOutput();
                    if (testKitDirectory != null)
                        runner = runner.withTestKitDir(testKitDirectory.toFile());
                    
                    BuildResult result = runner.build();
                    BuildTask taskResult = result.task(":runGradleTest");
                    System.out.println(gradleFile.getParent().getFileName().toString() + ": " + taskResult.getOutcome());
                    assertThat(taskResult.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
                };
                tests.add(dynamicTest(gradleFile.getParent().getFileName().toString(), gradleFile.toUri(), ex));
            }
        }

        return tests;
    }

    /**
     * @return arguments to gradle for running the test project.
     */
    protected List<String> getTestArguments()
    {
        return Arrays.asList("--info", "--stacktrace", "clean", "runGradleTest");
    }

    /**
     * Given a classpath path, returns the parent path.
     *
     * @param path classpath path.
     *
     * @return parent of the path.
     */
    private static String parentPath(String path)
    {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0)
            return "/";
        else
            return path.substring(0, slashIndex + 1);
    }

    /**
     * Runs test assumptions for a project to be tested.  This method checks filtering logic.  Subclasses may
     * add additional assumptions such as platform checks.
     *
     * @param gradleFile Gradle project file being tested.
     */
    protected void checkAssumptions(Path gradleFile)
    {
        //Check if system property is set that filters by test name
        String testName = gradleFile.getParent().getFileName().toString();
        assumeTrue(testFilter == null || testFilter.matcher(testName).matches(), "Test not executed due to gradletest filter being set.");
    }
}
