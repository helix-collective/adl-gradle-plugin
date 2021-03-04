package au.com.helixta.adl.gradle.generator;

import com.github.dockerjava.api.DockerClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RelativePath;
import org.gradle.api.model.ObjectFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File system mapper for running adlc in Docker containers.
 * <p>
 *
 * Copies files between Docker and host by using Docker commands and TAR archives.  This has proven to be more reliable than volumes/mounts.
 * Volumes/mounts have cross-platform issues such as perserving user permissions (sometimes files will written as read-only on the host or with root or
 * with an invalid user), inability to map all host directories (e.g. anything outside user home on Mac) and potentially running Docker on another machine.
 * Using TAR archives solves all of these problems, and given the amount of data being copied is minimal it is the safest option.
 * <p>
 *     
 * To use, after creating the mapper, register input and output files using addInputFiles() and registerOutputX().  When the container
 * is created, use {@link #copyFilesToContainer(String)} to copy files to the container, then use the container
 * as needed and afterwards copy output files back using {@link #copyFilesFromContainer(String)}.
 * <p>
 *
 * All files are read into memory when copying, so only small files should be used.
 */
public class DockerFileSystemMapper implements FileSystemMapper
{
    private final ObjectFactory objectFactory;
    private final DockerClient docker;
    private final ArchiveProcessor archiveProcessor;

    private final Map<FileTreeLabel, SourceTarArchive> inputFileTreeMap = new HashMap<>();
    private final Map<File, SourceTarArchive> inputFileMap = new HashMap<>();

    private final Map<File, String> outputDirectoryMap = new HashMap<>();
    private final Map<File, String> outputFileMap = new HashMap<>();

    public DockerFileSystemMapper(ObjectFactory objectFactory, ArchiveOperations archiveOperations, DockerClient docker)
    {
        this.objectFactory = Objects.requireNonNull(objectFactory);
        this.docker = Objects.requireNonNull(docker);
        this.archiveProcessor = new ArchiveProcessor(archiveOperations);
    }

    /**
     * Adds a file tree as input files for the container.  All files in the file tree will be copied to the container.
     *
     * @param fileTree a labelled file tree.
     * @param basePathInContainer the filesystem path in the container to copy the files to.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public void addInputFiles(LabelledFileTree fileTree, String basePathInContainer)
    throws AdlGenerationException
    {
        inputFileTreeMap.put(fileTree.getLabel(), createTarFromFileTree(fileTree.getTree(), basePathInContainer));
    }

    /**
     * Adds a directory of files as input for the container.  All files inside the directory will be copied recursively to the container.
     *
     * @param directory the directory to copy.
     * @param basePathInContainer the filesystem path in the container to copy the directory to.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public void addInputFiles(File directory, String basePathInContainer)
    throws AdlGenerationException
    {
        try
        {
            FileTree dirTree = archiveProcessor.archiveToFileTree(directory);
            if (dirTree == null)
                dirTree = objectFactory.fileTree().from(directory);

            SourceTarArchive dirTar = createTarFromFileTree(dirTree, basePathInContainer);
            inputFileMap.put(directory, dirTar);
        }
        catch (IOException e)
        {
            throw new AdlGenerationException("Error scanning file: " + directory, e);
        }
    }

    /**
     * Registers a directory that will receive contents copied from the container.
     *
     * @param hostDirectory the directory that will receive the contents.
     * @param basePathInContainer the directory inside the container to copy files from.
     */
    public void registerOutputDirectory(Directory hostDirectory, String basePathInContainer)
    {
        outputDirectoryMap.put(hostDirectory.getAsFile(), basePathInContainer);
    }

    /**
     * Registers a single file that will be copied from the container.
     *
     * @param file the file that will be saved on the host.
     * @param fileNameInContainer the path of the file in the container to copy.
     */
    public void registerOutputFile(RegularFile file, String fileNameInContainer)
    {
        outputFileMap.put(file.getAsFile(), fileNameInContainer);
    }

    /**
     * Copies all registered input files and directories to the container from the host.
     *
     * @param containerId the Docker container ID.
     */
    public void copyFilesToContainer(String containerId)
    {
        for (SourceTarArchive sourceTarArchive : inputFileTreeMap.values())
        {
            copySourceFilesFromTarToDockerContainer(sourceTarArchive, containerId);
        }
        for (SourceTarArchive sourceTarArchive : inputFileMap.values())
        {
            copySourceFilesFromTarToDockerContainer(sourceTarArchive, containerId);
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
     * Copies all registered output files and directories from the container to the host.
     *
     * @param containerId the Docker container ID.
     *
     * @return a map mapping output directories and output files to the numbers of files generated for each.  Output files will only have
     *         a count of zero or one.  Keys in this map will be files used in the registerOutputX() methods.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    public Map<? extends File, Integer> copyFilesFromContainer(String containerId)
    throws AdlGenerationException
    {
        Map<File, Integer> counters = new HashMap<>();

        for (Map.Entry<File, String> outputDirectoryEntry : outputDirectoryMap.entrySet())
        {
            Directory directory = objectFactory.directoryProperty().fileValue(outputDirectoryEntry.getKey()).get();
            int copyCount = copyFilesFromDockerContainer(outputDirectoryEntry.getValue(), directory, containerId);
            counters.put(outputDirectoryEntry.getKey(), copyCount);
        }
        for (Map.Entry<File, String> outputFileEntry : outputFileMap.entrySet())
        {
            boolean copied = copySingleFileFromDockerContainer(outputFileEntry.getValue(), outputFileEntry.getKey(), containerId);
            if (copied)
            {
                counters.put(outputFileEntry.getKey(), 1);
            }
        }

        return counters;
    }

    @Override
    public String targetInputDirectory(File directory)
    throws AdlGenerationException
    {
        return targetDirectory(directory);
    }

    @Override
    public String targetOutputDirectory(File directory)
    throws AdlGenerationException
    {
        return targetDirectory(directory);
    }

    private String targetDirectory(File directory)
    throws AdlGenerationException
    {
        SourceTarArchive mappedTar = inputFileMap.get(directory);
        if (mappedTar != null)
        {
            return mappedTar.getBaseDirectory();
        }

        String outputContainerPath = outputDirectoryMap.get(directory);
        if (outputContainerPath != null)
        {
            return outputContainerPath;
        }

        //If we get here there was no mapping registered
        throw new AdlGenerationException("Host directory " + directory + " was not mapped to Docker container.");
    }

    @Override
    public String targetFile(RegularFile file)
    throws AdlGenerationException
    {
        String fileNameInContainer = outputFileMap.get(file.getAsFile());
        if (fileNameInContainer == null)
        {
            throw new AdlGenerationException("Host file " + file + " was not mapped to Docker container.");
        }

        return fileNameInContainer;
    }

    @Override
    public List<String> targetFiles(LabelledFileTree fileTree)
    throws AdlGenerationException
    {
        SourceTarArchive mappedTar = inputFileTreeMap.get(fileTree.getLabel());
        if (mappedTar == null)
        {
            throw new AdlGenerationException("Host file tree " + fileTree.getLabel() + " was not mapped to Docker container.");
        }

        return mappedTar.getFilePaths();
    }

    private int copyFilesFromDockerContainer(String containerDirectory, Directory hostOutputDirectory, String containerId)
    throws AdlGenerationException
    {
        return copyFilesFromDockerContainer(containerDirectory, hostOutputDirectory, containerId, (dir, name) -> true);
    }

    /**
     * Copies a single file from a Docker container.
     *
     * @param containerFile the path of the file in the Docker container to copy.
     * @param hostFile the host file to save the contents to.
     * @param containerId the Docker container ID.
     *
     * @return true if a file was found and copied, false if no file was found and therefore not copied.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    private boolean copySingleFileFromDockerContainer(String containerFile, File hostFile, String containerId)
    throws AdlGenerationException
    {
        try (InputStream is = docker.copyArchiveFromContainerCmd(containerId, containerFile).exec();
             TarArchiveInputStream tis = new TarArchiveInputStream(is))
        {
            TarArchiveEntry entry;
            do
            {
                entry = tis.getNextTarEntry();
                if (entry != null && !entry.isDirectory())
                {
                    FileUtils.forceMkdirParent(hostFile);
                    Files.copy(tis, hostFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    //Single file copied, we're done
                    return true;
                }
            }
            while (entry != null);

            //If we get here, no files were copied
            return false;
        }
        catch (IOException e)
        {
            throw new AdlGenerationException(e);
        }
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
     * @throws AdlGenerationException if an error occurs.
     */
    private int copyFilesFromDockerContainer(String containerDirectory, Directory hostOutputDirectory, String containerId, FilenameFilter filter)
    throws AdlGenerationException
    {
        int generatedAdlFileCount = 0;

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
                    File adlOutputFile = hostOutputDirectory.file(relativeName).getAsFile();
                    if (filter.accept(adlOutputFile.getParentFile(), adlOutputFile.getName()))
                    {
                        FileUtils.forceMkdirParent(adlOutputFile);
                        Files.copy(tis, adlOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        generatedAdlFileCount++;
                    }
                }
            }
            while (entry != null);
        }
        catch (IOException e)
        {
            throw new AdlGenerationException(e);
        }

        return generatedAdlFileCount;
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
     * Creates an in-memory TAR archive from a file tree.
     *
     * @param sources a file tree whose files will be archived.
     * @param basePath the base directory to give all entries in the TAR archive.
     *
     * @return the created TAR archive.
     *
     * @throws AdlGenerationException if an error occurs.
     */
    private SourceTarArchive createTarFromFileTree(FileTree sources, String basePath)
    throws AdlGenerationException
    {
        List<String> filesInContainer = new ArrayList<>();

        //Generate TAR archive
        ByteArrayOutputStream tarBos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOs = new TarArchiveOutputStream(tarBos, "UTF-8"))
        {
            sources.visit(fileVisitDetails ->
                          {
                              String tarEntryFilePath = basePath + fileVisitDetails.getRelativePath().getPathString();
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
                                  throw new AdlGenerationRuntimeException(new AdlGenerationException("Error generating TAR file with ADL source files: " + e.getMessage(), e));
                              }
                          });
        }
        catch (IOException e)
        {
            throw new AdlGenerationException("Error generating TAR file with ADL source files: " + e.getMessage(), e);
        }
        catch (AdlGenerationRuntimeException e)
        {
            throw e.getCheckedException();
        }

        return new SourceTarArchive(new ByteArrayInputStream(tarBos.toByteArray()), basePath, filesInContainer);
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
     * Runtime exception wrapper for {@link AdlGenerationException}.
     */
    private static class AdlGenerationRuntimeException extends RuntimeException
    {
        private final AdlGenerationException checkedException;

        public AdlGenerationRuntimeException(AdlGenerationException cause)
        {
            super(cause);
            this.checkedException = Objects.requireNonNull(cause);
        }

        public AdlGenerationException getCheckedException()
        {
            return checkedException;
        }
    }
}
