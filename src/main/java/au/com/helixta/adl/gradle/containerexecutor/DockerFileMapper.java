package au.com.helixta.adl.gradle.containerexecutor;

import au.com.helixta.adl.gradle.generator.ArchiveProcessor;
import com.github.dockerjava.api.DockerClient;
import com.google.common.collect.ImmutableList;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.model.ObjectFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DockerFileMapper
{
    private final ObjectFactory objectFactory;
    private final DockerClient docker;
    private final ArchiveProcessor archiveProcessor;

    private final List<String> mappedCommandLine;

    /**
     * Container file arguments mapped to their docker container file/directory paths.
     */
    private final Map<? extends PreparedCommandLine.ContainerFile, String> containerFileMappings;

    /**
     * Container file tree arguments mapped to their docker container directory paths.
     */
    private final Map<? extends PreparedCommandLine.ContainerFileTree, String> containerFileTreeMappings;

    public DockerFileMapper(PreparedCommandLine commandLine, String dockerMappedFileBaseDirectory,
                            DockerClient docker, ObjectFactory objectFactory, ArchiveProcessor archiveProcessor)
    {
        this.docker = Objects.requireNonNull(docker);
        this.objectFactory = Objects.requireNonNull(objectFactory);
        this.archiveProcessor = Objects.requireNonNull(archiveProcessor);

        //Map host files into the container - maps host files to equivalent file paths inside the docker container
        Map<PreparedCommandLine.ContainerFile, String> containerFileMappings = new HashMap<>();
        for (PreparedCommandLine.ContainerFile argument : commandLine.getContainerFileArguments())
        {
            String mappedFile = FilenameUtils.separatorsToUnix(FilenameUtils.concat(dockerMappedFileBaseDirectory, argument.getLabel()));
            containerFileMappings.put(argument, mappedFile);
        }
        this.containerFileMappings = Collections.unmodifiableMap(containerFileMappings);

        //And same for mapped file trees
        Map<PreparedCommandLine.ContainerFileTree, String> containerFileTreeMappings = new HashMap<>();
        for (PreparedCommandLine.ContainerFileTree argument : commandLine.getContainerFileTreeArguments())
        {
            //Even though a tree might have multiple roots, when we copy to container the tree has a single base directory so a single root in the container
            String mappedTreeBaseDirectory = FilenameUtils.separatorsToUnix(FilenameUtils.concat(dockerMappedFileBaseDirectory, argument.getLabel()));
            containerFileTreeMappings.put(argument, mappedTreeBaseDirectory);
        }
        this.containerFileTreeMappings = Collections.unmodifiableMap(containerFileTreeMappings);

        //Generate the command line string including mapped file names
        List<String> mappedCommandLine = new ArrayList<>();
        for (PreparedCommandLine.Argument argument : commandLine.getArguments())
        {
            if (argument instanceof PreparedCommandLine.StringArgument)
                mappedCommandLine.add(((PreparedCommandLine.StringArgument)argument).getArgument());
            else if (argument instanceof PreparedCommandLine.ContainerFile)
            {
                PreparedCommandLine.ContainerFile fileArgument = (PreparedCommandLine.ContainerFile)argument;
                String mappedFile = Objects.requireNonNull(containerFileMappings.get(fileArgument), "Docker file should have been mapped");
                String argumentString = fileArgument.getCommandLineGenerator().generate(mappedFile);
                mappedCommandLine.add(argumentString);
            }
            else if (argument instanceof PreparedCommandLine.ContainerFileTree)
            {
                PreparedCommandLine.ContainerFileTree treeArgument = (PreparedCommandLine.ContainerFileTree)argument;
                String mappedTreeBaseDirectory = containerFileTreeMappings.get(treeArgument);
                List<String> baseArgs = treeArgument.getCommandLineGenerator().generateFromTree(treeArgument.getHostFileTree(), Collections.singletonList(mappedTreeBaseDirectory));
                mappedCommandLine.addAll(baseArgs);
                treeArgument.getHostFileTree().visit(fileVisitDetails ->
                {
                    String containerPath = FilenameUtils.separatorsToUnix(FilenameUtils.concat(mappedTreeBaseDirectory, fileVisitDetails.getRelativePath().getPathString()));
                    List<String> curArgs = treeArgument.getCommandLineGenerator().generateFromTreeElement(treeArgument.getHostFileTree(), fileVisitDetails, containerPath);
                    mappedCommandLine.addAll(curArgs);
                });
            }
            else
                throw new Error("Unknown argument type: " + argument.getClass().getName());
        }
        this.mappedCommandLine = Collections.unmodifiableList(mappedCommandLine);
    }

    public List<String> getMappedCommandLine()
    {
        return mappedCommandLine;
    }

    public List<String> getMappedCommandLineWithProgram(String program)
    {
        List<String> fullCommandLine = new ArrayList<>(mappedCommandLine.size() + 1);
        fullCommandLine.add(program);
        fullCommandLine.addAll(getMappedCommandLine());
        return fullCommandLine;
    }

    public void copyFilesFromHostToContainer(String dockerContainerId)
    throws IOException
    {
        //Iterate through all the files
        for (Map.Entry<? extends PreparedCommandLine.ContainerFile, String> mappingEntry : containerFileMappings.entrySet())
        {
            //Only copy file contents for files that are input or input/output
            if (mappingEntry.getKey().getFileMode() == PreparedCommandLine.FileTransferMode.INPUT || mappingEntry.getKey().getFileMode() == PreparedCommandLine.FileTransferMode.INPUT_OUTPUT)
            {
                if (mappingEntry.getKey().getFileType() == PreparedCommandLine.FileType.DIRECTORY)
                {
                    String containerDirectory = mappingEntry.getValue();

                    //Might be an archive file instead of directory - we want to support this
                    File directoryOrArchive = mappingEntry.getKey().getHostFile();
                    FileTree dirTree = archiveProcessor.archiveToFileTree(directoryOrArchive);

                    if (dirTree == null)
                        dirTree = objectFactory.fileTree().from(directoryOrArchive);

                    try (SourceTarArchive containerDirectoryTar = createTarFromFileTree(dirTree, containerDirectory))
                    {
                        copySourceFilesFromTarToDockerContainer(containerDirectoryTar, dockerContainerId);
                    }
                }
                else if (mappingEntry.getKey().getFileType() == PreparedCommandLine.FileType.SINGLE_FILE)
                {
                    //Single file
                    File singleFile = mappingEntry.getKey().getHostFile();
                    String containerFileName = mappingEntry.getValue();
                    try (SourceTarArchive singleFileTar = createTarFromSingleFile(singleFile, containerFileName))
                    {
                        copySourceFilesFromTarToDockerContainer(singleFileTar, dockerContainerId);
                    }
                }
                else
                    throw new Error("Unknown file type: " + mappingEntry.getKey().getFileType());
            }
            //For output-only files, only generate empty directories
            else if (mappingEntry.getKey().getFileMode() == PreparedCommandLine.FileTransferMode.OUTPUT)
            {
                String containerDirectory = mappingEntry.getValue();
                try (SourceTarArchive containerDirectoryTar = createEmptyDirectoryTar(containerDirectory))
                {
                    copySourceFilesFromTarToDockerContainer(containerDirectoryTar, dockerContainerId);
                }
            }
        }

        //Also process file trees
        for (Map.Entry<? extends PreparedCommandLine.ContainerFileTree, String> mappingEntry : containerFileTreeMappings.entrySet())
        {
            //All file trees are input only
            String containerDirectory = mappingEntry.getValue();
            FileTree dirTree = mappingEntry.getKey().getHostFileTree();

            try (SourceTarArchive containerDirectoryTar = createTarFromFileTree(dirTree, containerDirectory))
            {
                copySourceFilesFromTarToDockerContainer(containerDirectoryTar, dockerContainerId);
            }
        }
    }

    public void copyFilesFromContainerToHost(String dockerContainerId)
    throws IOException
    {
        Map<PreparedCommandLine.ContainerFile, Integer> copyFileCounts = new HashMap<>();

        for (Map.Entry<? extends PreparedCommandLine.ContainerFile, String> mappingEntry : containerFileMappings.entrySet())
        {
            //Only do this for files that are output or input/output
            if (mappingEntry.getKey().getFileMode() == PreparedCommandLine.FileTransferMode.OUTPUT || mappingEntry.getKey().getFileMode() == PreparedCommandLine.FileTransferMode.INPUT_OUTPUT)
            {
                if (mappingEntry.getKey().getFileType() == PreparedCommandLine.FileType.DIRECTORY)
                {
                    String containerDirectory = mappingEntry.getValue();

                    Directory directory = objectFactory.directoryProperty().fileValue(mappingEntry.getKey().getHostFile()).get();
                    int copyCount = copyFilesFromDockerContainer(containerDirectory, directory, dockerContainerId);
                    copyFileCounts.put(mappingEntry.getKey(), copyCount);
                }
                else if (mappingEntry.getKey().getFileType() == PreparedCommandLine.FileType.SINGLE_FILE)
                {
                    String containerFile = mappingEntry.getValue();
                    copySingleFileFromDockerContainer(containerFile, mappingEntry.getKey().getHostFile(), dockerContainerId);
                    copyFileCounts.put(mappingEntry.getKey(), 1);
                }
                else
                    throw new Error("Unknown file type: " + mappingEntry.getKey().getFileType());
            }
        }

        //TODO return counts?
    }

    /**
     * Copies files under a directory from the container to the host without filtering.
     *
     * @param containerDirectory the directory in the Docker container to copy.  All files under this directory are copied.
     * @param hostOutputDirectory the directory on the host to copy files to.
     * @param containerId the Docker container ID.
     *
     * @return the number of files copied.  Does not include directories.
     *
     * @throws IOException if an error occurs.
     */
    private int copyFilesFromDockerContainer(String containerDirectory, Directory hostOutputDirectory, String containerId)
    throws IOException
    {
        return copyFilesFromDockerContainer(containerDirectory, hostOutputDirectory, containerId, (dir, name) -> true);
    }

    /**
     * Copies files under a directory from the container to the host.
     *
     * @param containerDirectory the directory in the Docker container to copy.  All files under this directory are copied, subject to filtering.
     * @param hostOutputDirectory the directory on the host to copy files to.
     * @param containerId the Docker container ID.
     * @param filter a filter used to determine whether a file is copied to the host.
     *
     * @return the number of files copied.  Does not include directories.
     *
     * @throws IOException if an error occurs.
     */
    private int copyFilesFromDockerContainer(String containerDirectory, Directory hostOutputDirectory, String containerId, FilenameFilter filter)
    throws IOException
    {
        int generatedFileCount = 0;

        try (InputStream is = docker.copyArchiveFromContainerCmd(containerId, containerDirectory).exec();
             TarArchiveInputStream tis = new TarArchiveInputStream(is))
        {
            TarArchiveEntry entry;
            do
            {
                entry = tis.getNextTarEntry();
                if (entry != null && !entry.isDirectory())
                {
                    //Docker TAR archives have the last segment of the base directory in the TAR archive, so strip that out
                    String relativeName = relativizeTarPath(containerDirectory, entry.getName());

                    //Copy the file data to the host filesystem
                    File outputFile = hostOutputDirectory.file(relativeName).getAsFile();
                    if (filter.accept(outputFile.getParentFile(), outputFile.getName()))
                    {
                        FileUtils.forceMkdirParent(outputFile);
                        Files.copy(tis, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        generatedFileCount++;
                    }
                }
            }
            while (entry != null);
        }

        return generatedFileCount;
    }

    /**
     * Copies a single file from the container to the host.
     *
     * @param containerFile the path of the file in the Docker container to copy.
     * @param hostFile the destination file on the host.
     * @param containerId the Docker container ID.
     *
     * @return the number of files copied.  Does not include directories.
     *
     * @throws IOException if an error occurs.
     */
    private void copySingleFileFromDockerContainer(String containerFile, File hostFile, String containerId)
    throws IOException
    {
        boolean copied = false;
        try (InputStream is = docker.copyArchiveFromContainerCmd(containerId, containerFile).exec();
             TarArchiveInputStream tis = new TarArchiveInputStream(is))
        {
            TarArchiveEntry entry;
            do
            {
                entry = tis.getNextTarEntry();
                if (entry != null && !entry.isDirectory())
                {
                    //Copy the file data to the host filesystem
                    FileUtils.forceMkdirParent(hostFile);
                    Files.copy(tis, hostFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copied = true;
                }
            }
            while (entry != null);
        }

        if (!copied)
            throw new FileNotFoundException(containerFile + " not found in container " + containerId);
    }

    /**
     * Creates a relative path from a TAR entry.
     *
     * @param expectedBase the base directory to relativize against.
     * @param entryName an absolute container path.
     *
     * @return a relativized name if possible, or the entry name if the entry does not have the expected base.
     */
    private String relativizeTarPath(String expectedBase, String entryName)
    {
        //Docker puts the last segment of the base in the TAR entry, so remove it
        String lastBasePathSegment = RelativePath.parse(false, expectedBase).getLastName();

        RelativePath entryPath = RelativePath.parse(true, entryName);

        if (entryPath.getSegments()[0].equals(lastBasePathSegment))
        {
            RelativePath adjustedEntryPath = new RelativePath(true, Arrays.copyOfRange(entryPath.getSegments(), 1, entryPath.getSegments().length));
            return adjustedEntryPath.getPathString();
        }
        else //No adjustment needed
        {
            return entryName;
        }
    }

    /**
     * Copies contents of a TAR archive to a Docker container.
     *
     * @param sources TAR archive to copy.
     * @param dockerContainerId the Docker container ID.
     */
    private void copySourceFilesFromTarToDockerContainer(SourceTarArchive sources, String dockerContainerId)
    {
        docker.copyArchiveToContainerCmd(dockerContainerId)
              .withRemotePath("/") //All paths in TAR are absolute for the container
              .withTarInputStream(sources.getInputStream())
              .exec();
    }

    /**
     * Creates an in-memory TAR archive with a single empty directory entry.
     *
     * @param directoryPath the directory path to create an entry for.
     *
     * @return the created TAR archive.
     *
     * @throws IOException if an error occurs.
     */
    private SourceTarArchive createEmptyDirectoryTar(String directoryPath)
    throws IOException
    {
        String slashEndedBasePath;
        if (directoryPath.endsWith("/"))
            slashEndedBasePath = directoryPath;
        else
            slashEndedBasePath = directoryPath + "/";

        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            //TAR library makes anything ending with '/' a directory, so we're guaranteed a directory now
            TarArchiveEntry tarEntry = new TarArchiveEntry(slashEndedBasePath);
            tarEntry.setModTime(System.currentTimeMillis());
            tarOs.putArchiveEntry(tarEntry);
            tarOs.closeArchiveEntry();
        }

        return new SourceTarArchive(new ByteArrayInputStream(tarBos.toByteArray()), slashEndedBasePath, new ArrayList<>());
    }

    /**
     * Creates an in-memory TAR archive with a single file with a specific name.
     *
     * @param file the file to add to the TAR.
     * @param fileNameInTar the name of the file entry in the TAR.
     *
     * @return the created TAR archive.
     *
     * @throws IOException if an error occurs.
     */
    private SourceTarArchive createTarFromSingleFile(File file, String fileNameInTar)
    throws IOException
    {
        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            TarArchiveEntry tarEntry = new TarArchiveEntry(fileNameInTar);
            tarEntry.setModTime(file.lastModified());
            tarEntry.setSize(file.length());
            tarOs.putArchiveEntry(tarEntry);
            try (InputStream entryFileIs = new FileInputStream(file))
            {
                IOUtils.copy(entryFileIs, tarOs);
            }
            tarOs.closeArchiveEntry();
        }

        return new SourceTarArchive(new ByteArrayInputStream(tarBos.toByteArray()), FilenameUtils.getPath(fileNameInTar), ImmutableList.of(fileNameInTar));
    }

    /**
     * Creates an in-memory TAR archive from a file tree.
     *
     * @param sources a file tree whose files will be archived.
     * @param basePath the base directory to give all entries in the TAR archive.
     *
     * @return the created TAR archive.
     *
     * @throws IOException if an error occurs.
     */
    private SourceTarArchive createTarFromFileTree(FileTree sources, String basePath)
    throws IOException
    {
        String slashEndedBasePath;
        if (basePath.endsWith("/"))
            slashEndedBasePath = basePath;
        else
            slashEndedBasePath = basePath + "/";

        List<String> filesInContainer = new ArrayList<>();

        //Generate TAR archive
        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            sources.visit(fileVisitDetails ->
                          {
                              String tarEntryFilePath = slashEndedBasePath + fileVisitDetails.getRelativePath().getPathString();
                              if (fileVisitDetails.isDirectory() && !tarEntryFilePath.endsWith("/")) //TAR library makes anything ending with '/' a directory
                              {
                                  tarEntryFilePath = tarEntryFilePath + "/";
                              }

                              TarArchiveEntry tarEntry = new TarArchiveEntry(tarEntryFilePath);

                              tarEntry.setModTime(fileVisitDetails.getLastModified());
                              if (!fileVisitDetails.isDirectory())
                              {
                                  tarEntry.setSize(fileVisitDetails.getSize());
                              }

                              try
                              {
                                  tarOs.putArchiveEntry(tarEntry);
                                  if (!fileVisitDetails.isDirectory())
                                  {
                                      filesInContainer.add(tarEntryFilePath);
                                      try (InputStream entryFileIs = fileVisitDetails.open())
                                      {
                                          IOUtils.copy(entryFileIs, tarOs);
                                      }
                                  }
                                  tarOs.closeArchiveEntry();
                              }
                              catch (IOException e)
                              {
                                  throw new TarGenerationRuntimeException(e);
                              }
                          });
        }
        catch (TarGenerationRuntimeException e)
        {
            throw e.getCheckedException();
        }

        return new SourceTarArchive(new ByteArrayInputStream(tarBos.toByteArray()), slashEndedBasePath, filesInContainer);
    }

    /**
     * A TAR archive with attached metadata used for copying data between Docker containers and host.
     */
    private static class SourceTarArchive implements Closeable
    {
        private final InputStream inputStream;
        private final String baseDirectory;
        private final List<String> filePaths;

        public SourceTarArchive(InputStream inputStream, String baseDirectory, List<String> filePaths)
        {
            this.inputStream = inputStream;
            this.baseDirectory = baseDirectory;
            this.filePaths = new ArrayList<>(filePaths);
        }

        /**
         * @return the input stream of the TAR file.
         */
        public InputStream getInputStream()
        {
            return inputStream;
        }

        /**
         * @return the base directory of all entries in the TAR archive.
         */
        public String getBaseDirectory()
        {
            return baseDirectory;
        }

        /**
         * @return a list of paths in the TAR.
         */
        public List<String> getFilePaths()
        {
            return filePaths;
        }

        @Override
        public void close()
        throws IOException
        {
            inputStream.close();
        }
    }

    /**
     * Occurs when an error occurs generating a TAR archive.
     */
    private static class TarGenerationRuntimeException extends RuntimeException
    {
        private final IOException checkedException;

        public TarGenerationRuntimeException(IOException cause)
        {
            super(cause);
            this.checkedException = Objects.requireNonNull(cause);
        }

        public IOException getCheckedException()
        {
            return checkedException;
        }
    }
}
