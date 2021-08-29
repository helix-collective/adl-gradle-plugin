package au.com.helixta.adl.gradle.containerexecutor;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for dealing with file trees.
 */
final class FileTrees
{
    public static List<File> fileTreeRoots(FileTree hostFileTree)
    {
        //Configurable file trees will implement DirectoryTree
        if (hostFileTree instanceof DirectoryTree)
            return Collections.singletonList(((DirectoryTree)hostFileTree).getDir());

        Set<File> fileTreeRoots = new LinkedHashSet<>(); //All the roots detected in the file tree
        hostFileTree.visit(fileVisitDetails ->
        {
            File curBase = fileVisitDetails.getFile().getAbsoluteFile();
            String[] segments = fileVisitDetails.getRelativePath().getSegments();
            for (int i = segments.length - 1; i >= 0; i--)
            {
                if (segments[i].equals(curBase.getName()))
                    curBase = curBase.getParentFile();
                else
                    return; //Could not detect base, just bail out
            }

            fileTreeRoots.add(curBase);
        });

        return new ArrayList<>(fileTreeRoots);
    }

    /**
     * Reads the single root directory from a file tree, throwing an exception if there is not exactly one root.
     *
     * @param hostFileTree the file tree to read.
     *
     * @return the root directory of the file tree.
     *
     * @throws NoRootException if no roots are detected in the file tree, possibly because it is empty.
     * @throws MultipleRootsException if multiple roots are detected in the file tree.
     */
    public static File fileTreeRootDirectory(FileTree hostFileTree)
    throws NoRootException, MultipleRootsException
    {
        //Configurable file trees will implement DirectoryTree
        if (hostFileTree instanceof DirectoryTree)
            return ((DirectoryTree)hostFileTree).getDir();

        //Not a simple tree, try to detect all the roots by iterating entries and if there is a single one use it
        List<File> fileTreeRoots = fileTreeRoots(hostFileTree);
        if (fileTreeRoots.size() == 1)
            return fileTreeRoots.get(0);

        //Multiple roots detected, can't resolve to a single base directory
        if (fileTreeRoots.size() == 0)
            throw new NoRootException("Could not read root directory from file tree.");
        else
            throw new MultipleRootsException(fileTreeRoots);
    }

    public static class FileTreeRootException extends IOException
    {
        public FileTreeRootException()
        {
        }

        public FileTreeRootException(String message)
        {
            super(message);
        }
    }

    /**
     * Thrown when multiple roots were detected in a file tree when they were not expected.
     */
    public static class MultipleRootsException extends FileTreeRootException
    {
        private final List<File> roots;

        public MultipleRootsException(List<File> roots)
        {
            super(roots.size() + " roots detected in file tree: " + roots);
            this.roots = new ArrayList<>(roots);
        }

        /**
         * @return the multiple roots that were detected.
         */
        public List<File> getRoots()
        {
            return roots;
        }
    }

    /**
     * Occurs when no root is detected in a file tree.  This might happen when a file tree is empty.
     */
    public static class NoRootException extends FileTreeRootException
    {
        public NoRootException()
        {
        }

        public NoRootException(String message)
        {
            super(message);
        }
    }
}
