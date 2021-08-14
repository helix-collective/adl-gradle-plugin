package au.com.helixta.adl.gradle.containerexecutor;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTree;

import java.io.File;
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

    public static File fileTreeRootDirectory(FileTree hostFileTree)
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
            throw new RuntimeException("Could not read root directory from file tree.");
        else
            throw new RuntimeException("Multiple roots detected on file tree: " + fileTreeRoots);
    }
}
