package au.com.helixta.adl.manifesttest;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Ensures ADL manifest generation files are generated.
 */
class TestManifestGeneration
{
    @Test
    void javaManifestGenerated()
    throws IOException
    {
        List<String> manifestLines =
                Resources.readLines(TestManifestGeneration.class.getResource("/adl-java"), StandardCharsets.UTF_8);

        assertThat(manifestLines).contains("adl/test/sub/Cat.java");
    }

    @Test
    void typescriptManifestGenerated()
    throws IOException
    {
        List<String> manifestLines =
                Resources.readLines(TestManifestGeneration.class.getResource("/adl-typescript"), StandardCharsets.UTF_8);

        assertThat(manifestLines).contains("sub.ts", "resolver.ts");
    }
}
