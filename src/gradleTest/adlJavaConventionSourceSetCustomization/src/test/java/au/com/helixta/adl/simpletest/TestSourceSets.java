package au.com.helixta.adl.simpletest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TestSourceSets
{
    /**
     * Verify that Bird and Cat are generated, but Dog is not because there is an exclusion defined in the build file.
     */
    @Test
    void correctFilesAreGenerated()
    {
        assertThat(TestSourceSets.class.getResource("/adl/test/sub/Cat.java")).isNotNull();
        assertThat(TestSourceSets.class.getResource("/adl/test/sub2/Bird.java")).isNotNull();
        assertThat(TestSourceSets.class.getResource("/adl/test/sub3/Dog.java")).as("ADL file should be excluded").isNull();

    }
}
