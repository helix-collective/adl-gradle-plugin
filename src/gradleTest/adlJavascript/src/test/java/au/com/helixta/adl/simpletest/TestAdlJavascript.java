package au.com.helixta.adl.simpletest;

import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContextOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class TestAdlJavascript
{
    private static Engine jsEngine;
    private Context js;

    @BeforeAll
    private static void setUpJsEngine()
    {
        jsEngine = Engine.newBuilder().build();
    }

    @AfterAll
    private static void shutDownJsEngine()
    {
        if (jsEngine != null)
            jsEngine.close();
    }

    @BeforeEach
    private void setUpJsContext()
    {
        js = newContextBuilder()
                .build();
    }

    private Context.Builder newContextBuilder()
    {
        return Context.newBuilder(JavaScriptLanguage.ID)
                      .allowIO(true)
                      .engine(jsEngine)
                      .option(JSContextOptions.STRICT_NAME, "true")
                      .option(JSContextOptions.ECMASCRIPT_VERSION_NAME, String.valueOf(JSConfig.ECMAScript2017));
    }

    @AfterEach
    private void cleanUpJsContext()
    {
        if (js != null)
            js.close();
    }

    private static void copyResource(String resourceName, Path targetDirectory)
    throws IOException
    {
        Path targetFile = targetDirectory.resolve(resourceName);
        MoreFiles.createParentDirectories(targetFile);
        Files.write(targetFile, Resources.toByteArray(TestAdlJavascript.class.getResource("/" + resourceName)));
    }

    @Test
    void testReadingAdlMetadataFromJs(@TempDir Path jsWorkspace)
    throws IOException
    {
        copyResource("main/sub/cat.js", jsWorkspace);
        String src = "import {_ADL_TYPES} from 'main/sub/cat.js'; _ADL_TYPES['sub.cat.Cat'].fields";
        Path mainJsFile = jsWorkspace.resolve("main.js");
        Files.writeString(mainJsFile, src);
        //Use toFile, not URI because there's issues with URI encoding of paths on Windows platforms
        Source x = Source.newBuilder("js", mainJsFile.toFile())
                         .mimeType("application/javascript+module")
                         .uri(mainJsFile.toUri())
                         .build();

        Value result = js.eval(x);
        List<String> fieldNames = result.as(new TypeLiteral<List<Map<?, ?>>>(){})
                                        .stream()
                                        .map(item -> item.get("name").toString())
                                        .collect(Collectors.toList());

        assertThat(fieldNames).containsExactlyInAnyOrder("name", "age");
    }
}
