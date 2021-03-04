package au.com.helixta.adl.gradle.generator;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.util.List;

/**
 * Maps paths and file names between the local file system of Gradle and a hosted container.
 */
public interface FileSystemMapper
{
    /**
     * Given a local input directory, returns the corresponding path in the target filesystem.
     *
     * @param directory a local directory.
     *
     * @return path in the target filesystem.  May return null if the input directory should be ignored.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public default String targetInputDirectory(Directory directory)
    throws AdlGenerationException
    {
        return targetInputDirectory(directory.getAsFile());
    }

    /**
     * Given a local output directory, returns the corresponding path in the target filesystem.
     *
     * @param directory a local directory.
     *
     * @return path in the target filesystem.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public default String targetOutputDirectory(Directory directory)
    throws AdlGenerationException
    {
        return targetOutputDirectory(directory.getAsFile());
    }

    /**
     * Given a local input directory, returns the corresponding path in the target filesystem.
     *
     * @param directory a local directory.
     *
     * @return path in the target filesystem.  May return null if the input directory should be ignored.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public String targetInputDirectory(File directory)
    throws AdlGenerationException;

    /**
     * Given a local output directory, returns the corresponding path in the target filesystem.
     *
     * @param directory a local directory.
     *
     * @return path in the target filesystem.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public String targetOutputDirectory(File directory)
    throws AdlGenerationException;

    /**
     * Given a local regular file, returns the corresponding path in the target filesystem.
     *
     * @param file a local file.
     *
     * @return path in the target filesystem.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public String targetFile(RegularFile file)
    throws AdlGenerationException;

    /**
     * Given a local file tree, returns the list of files of the file tree in the target filesystem.
     *
     * @param fileTree a file tree with a label.
     *
     * @return a list of paths in the target filesystem.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public List<String> targetFiles(LabelledFileTree fileTree)
    throws AdlGenerationException;

    /**
     * Identifies a file tree.
     */
    public interface FileTreeLabel
    {
    }

    /**
     * File trees cannot be compared to for equality, so this class provides a way to label a file tree so it can be identified later.
     */
    public static class LabelledFileTree
    {
        private final FileTreeLabel label;
        private final FileTree tree;

        /**
         * Creates a labelled file tree.
         *
         * @param label label for the file tree.
         * @param tree the tree itself.
         */
        public LabelledFileTree(FileTreeLabel label, FileTree tree)
        {
            this.label = label;
            this.tree = tree;
        }

        public FileTreeLabel getLabel()
        {
            return label;
        }

        public FileTree getTree()
        {
            return tree;
        }
    }
}
