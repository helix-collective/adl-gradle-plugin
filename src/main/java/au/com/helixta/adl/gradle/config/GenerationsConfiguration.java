package au.com.helixta.adl.gradle.config;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@AdlDslMarker
public abstract class GenerationsConfiguration implements ExtensionAware
{
    private final List<JavaGenerationConfiguration> java = new ArrayList<>();
    private final List<TypescriptGenerationConfiguration> typescript = new ArrayList<>();
    private final List<JavascriptGenerationConfiguration> javascript = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Nested
    public List<JavaGenerationConfiguration> getJava()
    {
        return java;
    }

    @Nested
    public List<TypescriptGenerationConfiguration> getTypescript()
    {
        return typescript;
    }

    @Nested
    public List<JavascriptGenerationConfiguration> getJavascript()
    {
        return javascript;
    }

    public List<? extends GenerationConfiguration> allGenerations()
    {
        List<GenerationConfiguration> all = new ArrayList<>(java.size() + typescript.size());
        all.addAll(getJava());
        all.addAll(getTypescript());
        all.addAll(getJavascript());
        return all;
    }

    public void java(Action<? super JavaGenerationConfiguration> configuration)
    {
        JavaGenerationConfiguration j = getObjectFactory().newInstance(JavaGenerationConfiguration.class);
        configuration.execute(j);
        java.add(j);
    }

    public void typescript(Action<? super TypescriptGenerationConfiguration> configuration)
    {
        TypescriptGenerationConfiguration t = getObjectFactory().newInstance(TypescriptGenerationConfiguration.class);
        configuration.execute(t);
        typescript.add(t);
    }

    public void javascript(Action<? super JavascriptGenerationConfiguration> configuration)
    {
        JavascriptGenerationConfiguration t = getObjectFactory().newInstance(JavascriptGenerationConfiguration.class);
        configuration.execute(t);
        javascript.add(t);
    }

    public GenerationsConfiguration copyFrom(GenerationsConfiguration other)
    {
        for (JavaGenerationConfiguration otherConfig : other.getJava())
        {
            this.java(thisConfig -> thisConfig.copyFrom(otherConfig));
        }
        for (TypescriptGenerationConfiguration otherConfig : other.getTypescript())
        {
            this.typescript(thisConfig -> thisConfig.copyFrom(otherConfig));
        }
        for (JavascriptGenerationConfiguration otherConfig : other.getJavascript())
        {
            this.javascript(thisConfig -> thisConfig.copyFrom(otherConfig));
        }
        return this;
    }
}
