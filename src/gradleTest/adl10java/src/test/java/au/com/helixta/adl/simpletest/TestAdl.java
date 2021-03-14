package au.com.helixta.adl.simpletest;

import adl.test.sub.cat.Cat;
import adl.test.sub.cat.Vet;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that the generated ADL code exists and works as expected.
 */
class TestAdl
{
    @Test
    void simpleAdlTest()
    {
        Cat cat = new Cat("Dinah-Kah", 10);
        assertThat(cat.getName()).isEqualTo("Dinah-Kah");
        assertThat(cat.getAge()).isEqualTo(10);
    }

    @Test
    void fromJson()
    {
        Cat cat = Cat.jsonBinding().fromJsonString("{\"name\":\"Janvier\",\"age\":12}");
        assertThat(cat.getName()).isEqualTo("Janvier");
        assertThat(cat.getAge()).isEqualTo(12);
    }

    @Test
    void toJson()
    {
        Cat cat = new Cat("Dinah-Kah", 10);
        JsonObject json = Cat.jsonBinding().toJson(cat).getAsJsonObject();
        assertThat(json.getAsJsonPrimitive("name").getAsString()).isEqualTo("Dinah-Kah");
        assertThat(json.getAsJsonPrimitive("age").getAsInt()).isEqualTo(10);
    }

    @Test
    void vetFromTestSources()
    {
        //Would only work for ADL >= 1.0 since previous versions needed ArrayList instead of accepting any List
        Vet vet = new Vet("Dr. Snips", "Galahville", List.of(new Cat("Dinah-Kah", 10)));
        assertThat(vet.getName()).isEqualTo("Dr. Snips");
        assertThat(vet.getClinicLocation()).isEqualTo("Galahville");
    }
}
