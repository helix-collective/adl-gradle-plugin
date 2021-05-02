package au.com.helixta.adl.gradle.functest;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.*;

class FirstFunctionalTest
{
    @TempDir
    Path testProjectDir;

    //private File settingsFile;
    //private File buildFile;

    @BeforeEach
    private void setup()
    {
        //settingsFile = new File(testProjectDir, "settings.gradle");
        //buildFile = new File(testProjectDir, "build.gradle");
    }

    @TestFactory
    List<DynamicTest> testScanning()
    throws IOException
    {
        System.out.println("Test dir: " + Paths.get(".").toRealPath());

        System.out.println("Classpath: " + PluginUnderTestMetadataReading.readImplementationClasspath());

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
                System.out.println(buildGradlePath);
                String parentPath = parentPath(buildGradlePath);

                //Copy resources to target project dir
                for (Resource child : scanResult.getResourcesMatchingPattern(Pattern.compile("^" + Pattern.quote(parentPath) + ".*")))
                {
                    System.out.println(" > " + child + " (" + child.getPathRelativeToClasspathElement() + ")");
                    Path targetFile = testProjectDir.resolve(child.getPathRelativeToClasspathElement());
                    Files.createDirectories(targetFile.getParent());
                    try (InputStream is = child.open())
                    {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                //Set up and create the test
                Path gradleFile = testProjectDir.resolve(buildGradlePath);

                Executable ex = () -> {
                    System.out.println("Running " + buildGradlePath);
                    BuildResult result = GradleRunner.create()
                                                     //.withDebug(true)
                                                     .withProjectDir(gradleFile.getParent().toFile())
                                                     .withArguments("runGradleTest")
                                                     .withPluginClasspath()
                                                     .forwardOutput()
                                                     .build();
                    System.out.println(gradleFile.getParent().getFileName().toString() + ": " + result.task(":runGradleTest").getOutcome());
                };
                tests.add(dynamicTest(gradleFile.getParent().getFileName().toString(), gradleFile.toUri(), ex));
            }
        }

        return tests;
    }

    private static String parentPath(String path)
    {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0)
            return "/";
        else
            return path.substring(0, slashIndex + 1);
    }

    /*
    //@Test
    void test()
    throws IOException
    {
        Files.write(buildFile.toPath(), Arrays.asList(
                "task helloWorld {",
                "    doLast {",
                "        logger.quiet 'Hello world!'",
                "    }",
                "}"
        ));

        BuildResult result = GradleRunner.create()
                                         //.withDebug(true)
                                         .withProjectDir(testProjectDir)
                                         .withArguments("helloWorld")
                                         .withPluginClasspath()
                                         //.forwardOutput()
                                         .build();

        assertThat(result.task(":helloWorld").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        //System.out.println(result);
        //System.out.println(result.getOutput());
    }
     */
}
