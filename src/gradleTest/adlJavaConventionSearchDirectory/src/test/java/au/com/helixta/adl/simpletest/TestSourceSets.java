package au.com.helixta.adl.simpletest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TestSourceSets
{
    /**
     * Verify Java files are generated.
     */
    @Test
    void correctFilesAreGenerated()
    {
        assertThat(TestSourceSets.class.getResource("/adl/test/sub/Cat.java")).isNotNull();
    }
}
