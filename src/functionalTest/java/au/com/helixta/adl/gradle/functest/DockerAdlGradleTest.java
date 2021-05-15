package au.com.helixta.adl.gradle.functest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs Gradle integration test projects with Docker ADL platform configured.
 */
class DockerAdlGradleTest extends BaseGradleTestCase
{
    @Override
    protected Path getTestWorkspace(Path projectWorkspace)
    {
        return projectWorkspace.resolve("docker");
    }

    @Override
    protected List<String> getTestArguments()
    {
        List<String> args = new ArrayList<>(super.getTestArguments());
        args.addAll(Arrays.asList(
                "-Dadl.platform=DOCKER"
        ));
        return args;
    }
}
