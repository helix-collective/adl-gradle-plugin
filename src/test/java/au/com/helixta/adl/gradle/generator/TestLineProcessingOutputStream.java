package au.com.helixta.adl.gradle.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestLineProcessingOutputStream
{
    @Test
    void simpleLines()
    throws IOException
    {
        List<String> lines = new ArrayList<>();

        LineProcessingOutputStream os = new LineProcessingOutputStream(StandardCharsets.UTF_8, lines::add);
        os.write("Hello\nto\nyou\n".getBytes(StandardCharsets.UTF_8));
        os.close();

        assertThat(lines).containsExactly("Hello", "to", "you");
    }

    @Test
    void differentLineEndings()
    throws IOException
    {
        List<String> lines = new ArrayList<>();

        LineProcessingOutputStream os = new LineProcessingOutputStream(StandardCharsets.UTF_8, lines::add);
        os.write("Hello\r\nto\nyou".getBytes(StandardCharsets.UTF_8));
        os.close();

        assertThat(lines).containsExactly("Hello", "to", "you");
    }

    @Test
    void multibyteChars()
    throws IOException
    {
        List<String> lines = new ArrayList<>();

        LineProcessingOutputStream os = new LineProcessingOutputStream(StandardCharsets.UTF_8, lines::add);
        os.write("Euro\n".getBytes(StandardCharsets.UTF_8));
        os.write(new byte[] {(byte)-30, (byte)-126, (byte)-84, '\n'}); //Euro in UTF-8
        os.write("Last line".getBytes(StandardCharsets.UTF_8));
        os.close();

        assertThat(lines).containsExactly("Euro", "\u20AC" /* euro symbol */, "Last line");
    }
}
