package au.com.helixta.adl.gradle.config;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class GenerationsConfiguration implements ExtensionAware
{
    private final List<JavaGenerationConfiguration> java = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Nested
    public List<JavaGenerationConfiguration> getJava()
    {
        return java;
    }

    public void java(Action<? super JavaGenerationConfiguration> configuration)
    {
        JavaGenerationConfiguration j = getObjectFactory().newInstance(JavaGenerationConfiguration.class);
        configuration.execute(j);
        java.add(j);
    }
}
