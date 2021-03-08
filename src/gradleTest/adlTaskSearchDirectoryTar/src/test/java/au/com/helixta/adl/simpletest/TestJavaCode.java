package au.com.helixta.adl.simpletest;

import adl.test.sub.Cat;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

class TestJavaCode
{
    private static CompilationUnit compilationUnit;

    @BeforeAll
    private static void readSourceFile()
    throws IOException
    {
        try (InputStream is = Cat.class.getResource(Cat.class.getSimpleName() + ".java").openStream())
        {
            compilationUnit = StaticJavaParser.parse(is);
        }
    }

    @Test
    void packageName()
    {
        assertThat(compilationUnit.getPackageDeclaration().get().getName().asString()).isEqualTo("adl.test.sub");
    }

    @Test
    void fileHeaderComment()
    {
        assertThat(compilationUnit.getOrphanComments()).as("Generated ADL header comment of %s.java", Cat.class.getSimpleName()).anySatisfy(
                comment -> assertThat(comment.getContent()).isEqualToNormalizingWhitespace("* Full\n* of\n* galahs")
        );
    }
}
