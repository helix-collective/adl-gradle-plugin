package au.com.helixta.adl.gradle.containerexecutor;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTree;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builder of command lines for tools that can run natively or in containers.
 */
public class PreparedCommandLine
{
    private final List<Argument> arguments = new ArrayList<>();

    /**
     * Adds a string argument to the command line.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(String argument)
    {
        arguments.add(new StringArgument(argument));
        return this;
    }

    /**
     * Adds a mapped file argument to the command line.  When used, file arguments are mapped into the container.
     *
     * @param hostFile the file or directory on the host.
     * @param label a label for the file or directory argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(File hostFile, String label, FileTransferMode fileTransferMode, FileType fileType)
    {
        arguments.add(new ContainerFile(label, hostFile, fileTransferMode, fileType));
        return this;
    }

    /**
     * Adds a mapped file tree argument to the command line.  File trees can only be mapped from host to container.  The root directory of the file tree will be used
     * as the actual argument in the command line string.
     *
     * @param hostFileTree the file tree on the host.
     * @param label the label for the tree.  Used for generating the base directory in the container.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(FileTree hostFileTree, String label)
    {
        arguments.add(new ContainerFileTree(label, hostFileTree));
        return this;
    }

    /**
     * @return a list of all arguments in the command line.
     */
    public List<? extends Argument> getArguments()
    {
        return new ArrayList<>(arguments);
    }

    /**
     * @return a list of all container file arguments in the command line.
     */
    public List<? extends ContainerFile> getContainerFileArguments()
    {
        return arguments.stream()
                        .flatMap(arg -> arg instanceof ContainerFile ? Stream.of((ContainerFile)arg) : Stream.of())
                        .collect(Collectors.toList());
    }

    /**
     * @return a list of all container file tree arguments in the command line.
     */
    public List<? extends ContainerFileTree> getContainerFileTreeArguments()
    {
        return arguments.stream()
                        .flatMap(arg -> arg instanceof ContainerFileTree ? Stream.of((ContainerFileTree)arg) : Stream.of())
                        .collect(Collectors.toList());
    }

    /**
     * Base argument class.
     */
    public static abstract class Argument
    {
    }

    /**
     * String argument.
     */
    public static class StringArgument extends Argument
    {
        private final String argument;

        public StringArgument(String argument)
        {
            this.argument = Objects.requireNonNull(argument);
        }

        /**
         * @return the argument value.
         */
        public String getArgument()
        {
            return argument;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof StringArgument)) return false;
            StringArgument that = (StringArgument) o;
            return getArgument().equals(that.getArgument());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getArgument());
        }
    }

    /**
     * Argument representing a file name that needs to be mapped from host to container.
     */
    public static class ContainerFile extends Argument
    {
        private final String label;
        private final File hostFile;
        private final FileTransferMode fileTransferMode;
        private final FileType fileType;

        public ContainerFile(String label, File hostFile, FileTransferMode fileTransferMode, FileType fileType)
        {
            this.label = Objects.requireNonNull(label);
            this.hostFile = Objects.requireNonNull(hostFile);
            this.fileTransferMode = Objects.requireNonNull(fileTransferMode);
            this.fileType = Objects.requireNonNull(fileType);
        }

        /**
         * @return a label for the file or directory argument.  Used for generating file names in the container.
         */
        public String getLabel()
        {
            return label;
        }

        /**
         * @return the file or directory on the host system.
         */
        public File getHostFile()
        {
            return hostFile;
        }

        /**
         * @return file mode that determines how and when the file will be updated between host and container.
         */
        public FileTransferMode getFileMode()
        {
            return fileTransferMode;
        }

        /**
         * @return whether a directory (or archive) or a single file is being copied.
         */
        public FileType getFileType()
        {
            return fileType;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof ContainerFile)) return false;
            ContainerFile that = (ContainerFile) o;
            return getLabel().equals(that.getLabel());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getLabel());
        }
    }

    /**
     * Argument representing a file tree that needs to be mapped from host to container.
     */
    public static class ContainerFileTree extends Argument
    {
        private final String label;
        private final FileTree hostFileTree;

        public ContainerFileTree(String label, FileTree hostFileTree)
        {
            this.label = Objects.requireNonNull(label);
            this.hostFileTree = Objects.requireNonNull(hostFileTree);
        }

        /**
         * @return a label for the file or directory argument.  Used for generating file names in the container.
         */
        public String getLabel()
        {
            return label;
        }

        /**
         * @return the file tree on the host system.
         */
        public FileTree getHostFileTree()
        {
            return hostFileTree;
        }

        public File getHostFileTreeRootDirectory()
        {
            //Configurable file trees will implement DirectoryTree
            if (hostFileTree instanceof DirectoryTree)
                return ((DirectoryTree)hostFileTree).getDir();

            //Otherwise we have to do this the hard way, though this might result in multiple roots
            throw new RuntimeException("Could not read root directory from file tree.");
            //hostFileTree.visit(fileVisitDetails -> fileVisitDetails.getRelativePath()...)
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof ContainerFileTree)) return false;
            ContainerFileTree that = (ContainerFileTree) o;
            return getLabel().equals(that.getLabel());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getLabel());
        }
    }

    /**
     * Determines how and when a file will be updated between host and container.
     */
    public static enum FileTransferMode
    {
        /**
         * An input-only file for the container.  File is transferred to the container before execution but updates are not sent back to the host.
         */
        INPUT,

        /**
         * An output-only file for the container.  File is generated by the container and transferred back to the host after execution.
         */
        OUTPUT,

        /**
         * File is transferred to the container before execution and transferred back after execution.
         */
        INPUT_OUTPUT
    }

    /**
     * Determines whether file should be treated as a directory or a collection of files, or a single file.
     */
    public static enum FileType
    {
        /**
         * File is a directory or an archive that will be treated as a directory of files.
         */
        DIRECTORY,

        /**
         * File is a single file, not a directory.
         */
        SINGLE_FILE;
    }
}
