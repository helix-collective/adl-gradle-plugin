package au.com.helixta.adl.gradle.functest;

import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeAdlGradleTest extends BaseGradleTestCase
{
    @Override
    protected Path getTestWorkspace(Path projectWorkspace)
    {
        return projectWorkspace.resolve("native");
    }

    @Override
    protected List<String> getTestArguments()
    {
        List<String> args = new ArrayList<>(super.getTestArguments());
        args.addAll(Arrays.asList(
                "-Dadl.platform=NATIVE"
        ));
        return args;
    }

    @Override
    protected void checkAssumptions()
    {
        assumeTrue(OS.MAC.isCurrentOs() || OS.LINUX.isCurrentOs(),
                   "This OS/platform does not support native ADL execution, so cannot run the ADL native integration tests");
    }
}
