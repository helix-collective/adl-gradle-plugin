package au.com.helixta.adl.gradle.containerexecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ExecutorArgumentBuilder<F extends FileLabel>
{
    private final List<Object> arguments = new ArrayList<>();

    /**
     * Adds a string argument.
     *
     * @param arg the argument to add.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> string(String arg)
    {
        arguments.add(arg);
        return this;
    }

    /**
     * Adds an input directory argument.
     *
     * @param label label for the directory.
     * @param directory a local directory.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> inputDirectory(F label, File directory)
    {
        arguments.add(new LabelledFile<>(label, directory, FileType.DIRECTORY, FileMode.INPUT));
        return this;
    }

    /**
     * Adds an output directory argument.
     *
     * @param label label for the directory.
     * @param directory a local directory.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> outputDirectory(F label, File directory)
    {
        arguments.add(new LabelledFile<>(label, directory, FileType.DIRECTORY, FileMode.OUTPUT));
        return this;
    }

    /**
     * Adds an input/output directory argument.
     *
     * @param label label for the directory.
     * @param directory a local directory.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> inputOutputDirectory(F label, File directory)
    {
        arguments.add(new LabelledFile<>(label, directory, FileType.DIRECTORY, FileMode.INPUT, FileMode.OUTPUT));
        return this;
    }

    /**
     * Adds an input file argument.
     *
     * @param label label for the file.
     * @param file a local file.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> inputFile(F label, File file)
    {
        arguments.add(new LabelledFile<>(label, file, FileType.FILE, FileMode.INPUT));
        return this;
    }

    /**
     * Adds an output file argument.
     *
     * @param label label for the file.
     * @param file a local file.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> outputFile(F label, File file)
    {
        arguments.add(new LabelledFile<>(label, file, FileType.FILE, FileMode.OUTPUT));
        return this;
    }

    /**
     * Adds an input/output file argument.
     *
     * @param label label for the directory.
     * @param file a local file.
     *
     * @return this builder.
     */
    public ExecutorArgumentBuilder<F> inputOutputFile(F label, File file)
    {
        arguments.add(new LabelledFile<>(label, file, FileType.FILE, FileMode.INPUT, FileMode.OUTPUT));
        return this;
    }

    public static class LabelledFile<F>
    {
        private final F label;
        private final File file;
        private final FileType fileType;
        private final Set<FileMode> fileModes;

        public LabelledFile(F label, File file, FileType fileType, FileMode fileMode, FileMode... otherModes)
        {
            this.label = label;
            this.file = file;
            this.fileType = fileType;
            this.fileModes = EnumSet.of(fileMode, otherModes);
        }

        public F getLabel()
        {
            return label;
        }

        public File getFile()
        {
            return file;
        }

        public FileType getFileType()
        {
            return fileType;
        }

        public Set<FileMode> getFileModes()
        {
            return fileModes;
        }
    }

    public static enum FileType
    {
        FILE,
        DIRECTORY
    }

    public static enum FileMode
    {
        INPUT,
        OUTPUT
    }
}
