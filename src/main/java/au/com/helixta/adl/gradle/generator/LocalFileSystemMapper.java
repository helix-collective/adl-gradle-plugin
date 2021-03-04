package au.com.helixta.adl.gradle.generator;

import org.gradle.api.file.RegularFile;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class LocalFileSystemMapper implements FileSystemMapper
{
    @Override
    public String targetInputDirectory(File directory)
    throws AdlGenerationException
    {
        return directory.getAbsolutePath() + File.separator;
    }

    @Override
    public String targetOutputDirectory(File directory)
    throws AdlGenerationException
    {
        return directory.getAbsolutePath() + File.separator;
    }


    @Override
    public String targetFile(RegularFile file)
    throws AdlGenerationException
    {
        return file.getAsFile().getAbsolutePath();
    }

    @Override
    public List<String> targetFiles(LabelledFileTree fileTree)
    throws AdlGenerationException
    {
        return fileTree.getTree().getFiles()
                                 .stream()
                                 .map(File::getAbsolutePath)
                                 .collect(Collectors.toList());
    }
}
