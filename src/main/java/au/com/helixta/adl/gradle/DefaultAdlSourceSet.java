package au.com.helixta.adl.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

import javax.annotation.Nullable;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.ConfigureUtil.configure;

public class DefaultAdlSourceSet implements AdlSourceSet, HasPublicType
{
    private final SourceDirectorySet adl;

    public DefaultAdlSourceSet(String name, String displayName, ObjectFactory objectFactory)
    {
        adl = objectFactory.sourceDirectorySet(name, displayName +  " ADL source");
        adl.getFilter().include("**/*.adl");
    }

    @Override
    public SourceDirectorySet getAdl()
    {
        return adl;
    }

    @Override
    public AdlSourceSet adl(@Nullable Closure<?> configureClosure)
    {
        configure(configureClosure, getAdl());
        return this;
    }

    @Override
    public AdlSourceSet adl(Action<? super SourceDirectorySet> configureAction)
    {
        configureAction.execute(getAdl());
        return this;
    }

    @Override
    public TypeOf<?> getPublicType()
    {
        return typeOf(AdlSourceSet.class);
    }
}
