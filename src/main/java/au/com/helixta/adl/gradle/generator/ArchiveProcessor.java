package au.com.helixta.adl.gradle.generator;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Converts archive files to file trees that can be used to process files inside of them.
 */
public class ArchiveProcessor
{
    private final ArchiveOperations archiveOperations;

    public ArchiveProcessor(ArchiveOperations archiveOperations)
    {
        this.archiveOperations = Objects.requireNonNull(archiveOperations);
    }

    /**
     * If a file is a supported archive, turn it into a file tree.
     *
     * @param possibleArchive a local file on the filesystem that may be an archive.
     *
     * @return if the file is an archive, return a filetree that can read the archive.  Otherwise return null.
     *
     * @throws IOException if an I/O error occurs.
     */
    public FileTree archiveToFileTree(File possibleArchive)
    throws IOException
    {
        if (isTarFile(possibleArchive))
            return archiveOperations.tarTree(possibleArchive);
        if (isZipFile(possibleArchive))
            return archiveOperations.zipTree(possibleArchive);

        //Not an archive
        return null;
    }

    protected boolean isZipFile(File file)
    throws IOException
    {
        if (!file.isFile())
            return false;

        String lowerName = file.getName().toLowerCase(Locale.ROOT);

        return lowerName.endsWith(".zip") || lowerName.endsWith(".jar");
    }

    protected boolean isTarFile(File file)
    throws IOException
    {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".tar");
    }
}
