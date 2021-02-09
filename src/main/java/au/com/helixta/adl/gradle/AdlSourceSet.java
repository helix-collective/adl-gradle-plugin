package au.com.helixta.adl.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;

import javax.annotation.Nullable;

public interface AdlSourceSet
{
    SourceDirectorySet getAdl();
    AdlSourceSet adl(@Nullable Closure<?> configureClosure);
    AdlSourceSet adl(Action<? super SourceDirectorySet> configureAction);
}
