package au.com.helixta.adl.gradle.config;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.OutputDirectory;

import javax.inject.Inject;
import java.io.File;

@AdlDslMarker
public abstract class SqlSchemaGenerationConfiguration
{
    private final DirectoryProperty outputDirectory;

    public SqlSchemaGenerationConfiguration()
    {
        outputDirectory = getObjectFactory().directoryProperty();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @OutputDirectory
    public DirectoryProperty getOutputDirectory()
    {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory.fileValue(outputDirectory);
    }

    protected void baseCopyFrom(SqlSchemaGenerationConfiguration other)
    {
        outputDirectory.set(other.outputDirectory);
    }
}
