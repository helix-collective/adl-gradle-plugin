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
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

abstract class BaseGradleTestCase
{
    @TempDir
    Path tempDir;

    private Path projectWorkspace;

    @BeforeEach
    private void setup()
    {
        String workspaceProperty = System.getProperty("test.projectworkspace.directory");
        if (workspaceProperty != null)
            workspaceProperty = workspaceProperty.trim();
        if (workspaceProperty != null && workspaceProperty.isEmpty())
            workspaceProperty = null;
        if (workspaceProperty != null)
            projectWorkspace = Paths.get(workspaceProperty);
        else
            projectWorkspace = tempDir;
    }

    protected abstract Path getTestWorkspace(Path projectWorkspace);

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
                    checkAssumptions();
                    System.out.println("Running " + buildGradlePath);
                    BuildResult result = GradleRunner.create()
                                                     //.withDebug(true)
                                                     .withProjectDir(gradleFile.getParent().toFile())
                                                     .withArguments(getTestArguments())
                                                     .withPluginClasspath()
                                                     .forwardOutput()
                                                     .build();
                    BuildTask taskResult = result.task(":runGradleTest");
                    System.out.println(gradleFile.getParent().getFileName().toString() + ": " + taskResult.getOutcome());
                    assertThat(taskResult.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
                };
                tests.add(dynamicTest(gradleFile.getParent().getFileName().toString(), gradleFile.toUri(), ex));
            }
        }

        return tests;
    }

    protected List<String> getTestArguments()
    {
        return Arrays.asList("--info", "--stacktrace", "clean", "runGradleTest");
    }

    private static String parentPath(String path)
    {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0)
            return "/";
        else
            return path.substring(0, slashIndex + 1);
    }

    protected void checkAssumptions()
    {
        //No base assumptions
    }
}
