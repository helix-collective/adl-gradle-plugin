package au.com.helixta.adl.gradle.containerexecutor;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
     * Adds a mapped file argument to the command line with no prefix or suffix.  When used, file arguments are mapped into the container.
     *
     * @param hostFile the file or directory on the host.
     * @param label a label for the file or directory argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     * @param fileType if the file should be treated as a single file or a directory.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(File hostFile, String label, FileTransferMode fileTransferMode, FileType fileType)
    {
        return argument(hostFile, label, fileTransferMode, fileType, containerPath -> containerPath);
    }

    /**
     * Adds a mapped file argument to the command line with a custom generator.  This can be used for generating command line arguments from mapped paths with
     * prefixes and suffixes.  When used, file arguments are mapped into the container.
     *
     * @param hostFile the file or directory on the host.
     * @param label a label for the file or directory argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     * @param fileType if the file should be treated as a single file or a directory.
     * @param generator controls how the mapped container file is mapped into a command line argument string.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(File hostFile, String label, FileTransferMode fileTransferMode, FileType fileType, FileCommandLineGenerator generator)
    {
        arguments.add(new ContainerFile(label, hostFile, fileTransferMode, fileType, generator));
        return this;
    }

    /**
     * Adds a mapped directory argument to the command line with no prefix or suffix.  When used, directory arguments are mapped into the container.
     *
     * @param hostDirectory the directory on the host.
     * @param label a label for the directory argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(Directory hostDirectory, String label, FileTransferMode fileTransferMode)
    {
        return argument(hostDirectory.getAsFile(), label, fileTransferMode, FileType.DIRECTORY);
    }

    /**
     * Adds a mapped directory argument to the command line with a custom generator.  This can be used for generating command line arguments from mapped paths with
     * prefixes and suffixes.  When used, directory arguments are mapped into the container.
     *
     * @param hostDirectory the directory on the host.
     * @param label a label for the directory argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     * @param generator controls how the mapped container file is mapped into a command line argument string.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(Directory hostDirectory, String label, FileTransferMode fileTransferMode, FileCommandLineGenerator generator)
    {
        return argument(hostDirectory.getAsFile(), label, fileTransferMode, FileType.DIRECTORY, generator);
    }

    /**
     * Adds a mapped regular file argument to the command line with no prefix or suffix.  When used, file arguments are mapped into the container.
     *
     * @param hostFile the file on the host.
     * @param label a label for the file argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(RegularFile hostFile, String label, FileTransferMode fileTransferMode)
    {
        return argument(hostFile.getAsFile(), label, fileTransferMode, FileType.SINGLE_FILE);
    }

    /**
     * Adds a mapped regular file argument to the command line with a custom generator.  This can be used for generating command line arguments from mapped paths with
     * prefixes and suffixes.  When used, file arguments are mapped into the container.
     *
     * @param hostFile the file on the host.
     * @param label a label for the file argument.  Used for generating file names in the container.
     * @param fileTransferMode determines how and when the file will be updated between host and container.
     * @param generator controls how the mapped container file is mapped into a command line argument string.
     *
     * @return this command line.
     */
    public PreparedCommandLine argument(RegularFile hostFile, String label, FileTransferMode fileTransferMode, FileCommandLineGenerator generator)
    {
        return argument(hostFile.getAsFile(), label, fileTransferMode, FileType.SINGLE_FILE, generator);
    }

    /**
     * Adds a mapped file tree argument to the command line with complete control over command line translation and generation.
     * File trees can only be mapped from host to container.
     *
     * @param hostFileTree the file tree on the host.
     * @param label the label for the tree.  Used for generating the base directory in the container.
     * @param fileTreeCommandLineGenerator the generator to use for generating command line arguments from file tree elements.
     *
     * @return this command line.
     *
     * @see #argument(FileTree, String)
     */
    public PreparedCommandLine argument(FileTree hostFileTree, String label, FileTreeCommandLineGenerator fileTreeCommandLineGenerator)
    {
        arguments.add(new ContainerFileTree(label, hostFileTree, fileTreeCommandLineGenerator));
        return this;
    }

    /**
     * Adds a mapped file tree argument to the command line that expands all file elements from the file tree into string arguments.
     * File trees can only be mapped from host to container.
     *
     * @param hostFileTree the file tree on the host.
     * @param label the label for the tree.  Used for generating the base directory in the container.
     *
     * @return this command line.
     *
     * @see #argument(FileTree, String, FileTreeCommandLineGenerator) 
     */
    public PreparedCommandLine argument(FileTree hostFileTree, String label)
    {
        arguments.add(new ContainerFileTree(label, hostFileTree, new SimpleFileCommandLineGenerator()));
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
        private final FileCommandLineGenerator commandLineGenerator;

        public ContainerFile(String label, File hostFile, FileTransferMode fileTransferMode, FileType fileType, FileCommandLineGenerator commandLineGenerator)
        {
            this.label = Objects.requireNonNull(label);
            this.hostFile = Objects.requireNonNull(hostFile);
            this.fileTransferMode = Objects.requireNonNull(fileTransferMode);
            this.fileType = Objects.requireNonNull(fileType);
            this.commandLineGenerator = Objects.requireNonNull(commandLineGenerator);
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

        /**
         * @return the generator to use for generating command line argument strings from container paths.
         */
        public FileCommandLineGenerator getCommandLineGenerator()
        {
            return commandLineGenerator;
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
        private final FileTreeCommandLineGenerator commandLineGenerator;

        public ContainerFileTree(String label, FileTree hostFileTree, FileTreeCommandLineGenerator commandLineGenerator)
        {
            this.label = Objects.requireNonNull(label);
            this.hostFileTree = Objects.requireNonNull(hostFileTree);
            this.commandLineGenerator = Objects.requireNonNull(commandLineGenerator);
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

        /**
         * @return the generator for translating elements from this file tree to actual command line arguments.
         */
        public FileTreeCommandLineGenerator getCommandLineGenerator()
        {
            return commandLineGenerator;
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

    /**
     * Interface to allow control over generation of a single command line argument from a file.  Allows string prefixes and suffixes to be added to
     * container-generated file names.
     */
    @FunctionalInterface
    public static interface FileCommandLineGenerator
    {
        /**
         * Generates a command line argument from a container path mapped from a host file or directory.
         *
         * @param containerPath the path of the file or directory in the container.
         *
         * @return a command line argument string.  Should incorporate the container path.
         */
        public String generate(String containerPath);
    }

    /**
     * Interface to allow complete control over the conversion of elements in a file tree to command line arguments.
     */
    public static interface FileTreeCommandLineGenerator
    {
        /**
         * Translates the file tree itself to command line arguments.  Can be used if only the base directories of the tree are needed to generate arguments.
         *
         * @param tree the file tree the element is from.
         * @param rootContainerPaths the root directories of the file tree mapped to containers.  Typically only one directory, but may be multiple if there are multiple
         *                           trees joined together.
         *
         * @return strings to add to the command line for this element, or an empty list if it should be ignored.
         */
        public List<String> generateFromTree(FileTree tree, List<String> rootContainerPaths);

        /**
         * Translates a single path in a file tree to command line arguments.
         *
         * @param tree the file tree the element is from.
         * @param element the element being processed from the file tree on the host.
         * @param containerPath the absolute path the element or tree itself has mapped inside the container.  Use this path instead of the one on the host when
         *                      generating arguments.
         *
         * @return strings to add to the command line for this element, or an empty list if it should be ignored.
         */
        public List<String> generateFromTreeElement(FileTree tree, FileTreeElement element, String containerPath);
    }

    /**
     * Translates files and not directories to string command line arguments without any prefix or suffix arguments.
     */
    public static class SimpleFileCommandLineGenerator implements FileTreeCommandLineGenerator
    {
        @Override
        public List<String> generateFromTree(FileTree tree, List<String> rootContainerPaths)
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> generateFromTreeElement(FileTree tree, FileTreeElement element, String containerPath)
        {
            if (element.isDirectory())
                return Collections.emptyList();

            return Collections.singletonList(containerPath);
        }
    }

    /**
     * Uses the base directory of the file tree as a single string argument.  Not all file trees have a single root, so be careful with this as it might not work everywhere.
     * Throws an exception if there are multiple or no roots in the tree.
     */
    public static class SingleBaseDirectoryCommandLineGenerator implements FileTreeCommandLineGenerator
    {
        @Override
        public List<String> generateFromTree(FileTree tree, List<String> rootContainerPaths)
        {
            return rootContainerPaths;
        }

        @Override
        public List<String> generateFromTreeElement(FileTree tree, FileTreeElement element, String containerPath)
        {
            return Collections.emptyList();
        }
    }
}
