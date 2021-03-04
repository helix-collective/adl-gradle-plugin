package au.com.helixta.adl.gradle.generator;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ArchiveExpandingLocalFileSystemMapper extends LocalFileSystemMapper
{
    private final ArchiveProcessor archiveProcessor;

    public ArchiveExpandingLocalFileSystemMapper(ArchiveOperations archiveOperations)
    {
        this.archiveProcessor = new ArchiveProcessor(archiveOperations);
    }

    @Override
    public String targetInputDirectory(File directory)
    throws AdlGenerationException
    {
        try
        {
            FileTree archiveFileTree = archiveProcessor.archiveToFileTree(directory);
            if (archiveFileTree != null)
                directory = resolveDirectoryOfArchive(archiveFileTree);
        }
        catch (IOException e)
        {
            throw new AdlGenerationException(e);
        }

        if (directory == null)
            return null;
        else
            return super.targetInputDirectory(directory);
    }


    private File resolveDirectoryOfArchive(FileTree archiveFileTree)
    throws IOException
    {
        Set<File> archiveRootDirs = new HashSet<>();
        archiveFileTree.visit(d ->
        {
            //d.getFile() will cause the file to be expanded into temp directory in Gradle
            //then resolve the temp directory itself by scanning ancestors

            int segmentsFromBase = d.getRelativePath().getSegments().length;
            File fa = d.getFile();
            for (int i = 0; i < segmentsFromBase; i++)
            {
                fa = fa.getParentFile();
            }

            archiveRootDirs.add(fa);
        });

        //At the end we should only have one or zero archive root dirs since the archive will only ever expand out
        //to a single temp directory
        //Zero roots is possible if there are no files in the archive
        if (archiveRootDirs.isEmpty())
            return null;
        else if (archiveRootDirs.size() == 1)
            return archiveRootDirs.iterator().next();
        else //Should never have more than one - indicates change in how Gradle expands archives to temp directory
            throw new IOException("Got multiple roots from expanded archive " + archiveFileTree + ": " + archiveRootDirs);
    }
}
