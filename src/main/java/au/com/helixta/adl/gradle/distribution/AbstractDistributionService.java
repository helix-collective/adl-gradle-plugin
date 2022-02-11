package au.com.helixta.adl.gradle.distribution;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractDistributionService implements DistributionService
{
    private final File unpackedDistributionInstallationDirectory;
    private final Project project;
    private final FileSystemOperations fileSystemOperations;
    private final ArchiveOperations archiveOperations;

    /**
     * Base URL for downloading distributions.
     */
    private final URI distributionBaseUrl;

    /**
     * Unprefixed simple name of the distribution used for repository name and local cache directory naming.
     */
    private final String distributionSimpleName;

    /**
     * Group ID used for distribution artifacts in the local repo.
     */
    private final String distributionGroupId;

    protected AbstractDistributionService(URI distributionBaseUrl,
                                          String distributionSimpleName,
                                          String distributionGroupId,
                                          GradleUserHomeDirProvider homeDirProvider,
                                          FileSystemOperations fileSystemOperations,
                                          ArchiveOperations archiveOperations,
                                          Project project)
    {
        this.distributionBaseUrl = Objects.requireNonNull(distributionBaseUrl);
        this.distributionSimpleName = Objects.requireNonNull(distributionSimpleName);
        this.distributionGroupId = Objects.requireNonNull(distributionGroupId);
        this.unpackedDistributionInstallationDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), distributionSimpleName);
        this.fileSystemOperations = fileSystemOperations;
        this.archiveOperations = archiveOperations;
        this.project = project;

    }

    /**
     * Given a distribution specifier, determines the classifier and archive extension that are used for generating the
     * download URL.  May return null, which indicates no distribution is available for the given specifier
     * ahead of time without checking remotely for download availability.
     *
     * @param specifier the distribution specifier specifying distribution version, OS and platform.
     *
     * @return classifier and archive extension strings, or null if no distribution is available for the given specifier.
     */
    protected abstract DownloadParameters specifierToDownloadParameters(DistributionSpecifier specifier);

    /**
     * For a given distribution specifier, returns the name of the installation directory that will be used for holding
     * the unpacked distribution.
     */
    protected String specToInstallationDirectoryName(DistributionSpecifier spec)
    {
        return distributionSimpleName + "-" + spec.getVersion() + "-" + osFamilyName(spec.getOs()) + "-" + spec.getArchitecture().getName().toLowerCase(
                Locale.ROOT);
    }

    protected static String osFamilyName(OperatingSystem os)
    {
        // can't use gradle's family name `macos` as git release binaries are `osx`
        if (os.isMacOsX())
            return "osx";
        if (os instanceof OperatingSystemInternal)
            return ((OperatingSystemInternal)os).toFamilyName();
        else if (os.isWindows())
            return OperatingSystemFamily.WINDOWS;
        else if (os.isLinux())
            return OperatingSystemFamily.LINUX;
        else
            return os.getName();
    }

    @Override
    public File resolveDistributionArchive(DistributionSpecifier spec)
    throws DistributionNotFoundException
    {
        DownloadParameters downloadParameters = specifierToDownloadParameters(spec);
        if (downloadParameters == null)
            throw new DistributionNotFoundException("No " + distributionSimpleName + " distribution available for OS: " + spec.getOs() + "/" + spec.getArchitecture());

        //Add download repo if needed
        //Set up a fake Ivy repository that can download from Github releases directly
        //This repo only supports our specific dependency
        project.getRepositories().ivy(r -> {
            r.setName(distributionSimpleName + "-distribution");
            r.setUrl(distributionBaseUrl);
            r.patternLayout(p -> {
                p.artifact("v[revision]/[artifact]-[revision]-[classifier].[ext]");
            });
            r.metadataSources(IvyArtifactRepository.MetadataSources::artifact); //No metadata files in Github
            r.content(c -> {
                c.includeGroup(distributionGroupId);
            });
        });

        //Then use Gradle's dependency system to download it
        //If it's already downloaded it will be cached locally and just give a reference to the file without additional download
        Dependency distributionArchiveDependency = project.getDependencies().create(distributionGroupId + ":" + downloadParameters.getArtifactId() + ":" + spec.getVersion() + ":" + downloadParameters.getClassifier() + "@" + downloadParameters.getExtension());
        Configuration distributionArchiveDependencyConfig = project.getConfigurations().detachedConfiguration(distributionArchiveDependency);
        distributionArchiveDependencyConfig.setTransitive(false);

        try
        {
            Set<File> distributionLocalArchives = distributionArchiveDependencyConfig.resolve();

            //Should never return anything other than single file since that's how the dependency was defined
            //Bail out if we get anything else that is unexpected
            if (distributionLocalArchives.size() != 1)
                throw new RuntimeException("Expected single dependency to download single file");

            return distributionLocalArchives.iterator().next();
        }
        catch (ResolveException e)
        {
            throw new DistributionNotFoundException("Failed to resolve distribution " + distributionArchiveDependency, e);
        }
    }

    @Override
    public File resolveDistribution(DistributionSpecifier spec)
    throws DistributionNotFoundException, IOException
    {
        //Attempt to locate an already-unpacked local version
        File distributionInstallationDir = new File(unpackedDistributionInstallationDirectory, specToInstallationDirectoryName(spec));
        if (distributionInstallationDir.exists())
            return distributionInstallationDir;

        //If it doesn't exist, download and unpack
        File distributionArchive = resolveDistributionArchive(spec);

        //Use a temporary directory so other processes won't pick up a half-installed distribution concurrently
        //and rename it to the proper name when done
        //Use nio instead of java.io.File because it has a createTempDirectory()
        Files.createDirectories(unpackedDistributionInstallationDirectory.toPath());
        Path unpackTempDir = Files.createTempDirectory(unpackedDistributionInstallationDirectory.toPath(), distributionSimpleName + "-install-");
        unpackDistribution(distributionArchive, unpackTempDir.toFile());
        Files.move(unpackTempDir, distributionInstallationDir.toPath());

        return distributionInstallationDir;
    }

    /**
     * Unpacks a distribution archive into a target directory.
     *
     * @param distributionArchive the archive file to unpack.
     * @param targetDirectory the directory to extract files to.
     */
    protected void unpackDistribution(File distributionArchive, File targetDirectory)
    {
        if (distributionArchive.getName().endsWith(".tar"))
        {
            fileSystemOperations.copy(copySpec -> {
                copySpec.from(archiveOperations.tarTree(distributionArchive))
                        .into(targetDirectory);
            });
        }
        else if (distributionArchive.getName().endsWith(".zip"))
        {
            fileSystemOperations.copy(copySpec -> {
                copySpec.from(archiveOperations.zipTree(distributionArchive))
                        .into(targetDirectory);
            });
        }
        else
            throw new RuntimeException("Cannot unpack unknown archive type: " + distributionArchive.getName());
    }

    protected static class DownloadParameters
    {
        private final String artifactId;
        private final String classifier;
        private final String extension;

        public DownloadParameters(String artifactId, String classifier, String extension)
        {
            this.artifactId = Objects.requireNonNull(artifactId);
            this.classifier = Objects.requireNonNull(classifier);
            this.extension = Objects.requireNonNull(extension);
        }

        /**
         * @return the artifact ID string used partly to build the distribution download URL.
         */
        public String getArtifactId()
        {
            return artifactId;
        }

        /**
         * @return the classifier string used partly to build the distribution download URL.
         */
        public String getClassifier()
        {
            return classifier;
        }

        /**
         * @return the archive extension.
         */
        public String getExtension()
        {
            return extension;
        }
    }
}
