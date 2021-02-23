package au.com.helixta.adl.gradle.distribution;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public class AdlDistributionService
{
    private final URI adlDistributionBaseUrl = URI.create("https://github.com/timbod7/adl/releases/download/");

    private final File adlBaseInstallationDirectory;
    private final Project project;

    private final FileSystemOperations fileSystemOperations;
    private final ArchiveOperations archiveOperations;

    @Inject
    public AdlDistributionService(GradleUserHomeDirProvider homeDirProvider, FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations, Project project)
    {
        this.adlBaseInstallationDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "adl");
        this.fileSystemOperations = fileSystemOperations;
        this.archiveOperations = archiveOperations;
        this.project = project;
    }

    /**
     * Normalizes an ADL distribution specification, normalizing architecture and OS names.
     * OS names and architectures for certain platforms can have many different names which we don't want to deal with everywhere.
     *
     * @param spec the spec to normalize.
     *
     * @return the normalized spec.
     */
    private AdlDistributionSpec normalizeSpec(AdlDistributionSpec spec)
    {
        String arch = spec.getArchitecture();
        if ("x86_64".equals(spec.getArchitecture()) || "x86-64".equals(spec.getArchitecture()))
            arch = "amd64";

        String os = spec.getOs().toLowerCase(Locale.ROOT);
        if (os.contains("mac os x") || os.contains("darwin") || os.contains("osx"))
            os = "osx";
        else if (os.contains("linux"))
            os = "linux";

        return new AdlDistributionSpec(spec.getVersion(), arch, os);
    }

    /**
     * For a given spec, make an archive classifier for downloading off of the ADL Github releases page.
     */
    private String specToClassifier(AdlDistributionSpec spec)
    {
        //Only 64-bit releases are available for ADL
        if (!"amd64".equals(spec.getArchitecture()))
            return null;

        //Only OSX/linux supported
        if ("osx".equals(spec.getOs()) || "linux".equals(spec.getOs()))
            return spec.getOs();

        //No other OSes supported
        return null;
    }

    /**
     * For a given spec, returns the name of the ADL installation directory.
     */
    private String specToInstallationDirectoryName(AdlDistributionSpec spec)
    {
        return "adl-" + spec.getVersion() + "-" + spec.getOs() + "-" + spec.getArchitecture();
    }

    /**
     * Resolves an ADL installation archive, downloading it from the distribution release page if not already downloaded.
     *
     * @param spec ADL release specification.
     *
     * @return a local file of the ADL release ZIP archive.
     *
     * @throws AdlDistributionNotFoundException if no distribution is available for the given spec.
     */
    public File resolveAdlDistributionArchive(AdlDistributionSpec spec)
    throws AdlDistributionNotFoundException
    {
        spec = normalizeSpec(spec);

        String classifier = specToClassifier(spec);
        if (classifier == null)
            throw new AdlDistributionNotFoundException("No ADL distribution available for OS: " + spec.getOs() + "/" + spec.getArchitecture());

        //Add download repo if needed
        //Set up a fake Ivy repository that can download from Github releases directly
        //This repo only supports our specific ADL dependency
        project.getRepositories().ivy(r -> {
            r.setName("adl-distribution");
            r.setUrl(adlDistributionBaseUrl);
            r.patternLayout(p -> {
                p.artifact("v[revision]/[artifact]-[revision]-[classifier].[ext]");
            });
            r.metadataSources(IvyArtifactRepository.MetadataSources::artifact); //No metadata files in Github
            r.content(c -> {
                c.includeModule("org.adl", "adl-bindist");
            });
        });

        //Then use Gradle's dependency system to download it
        //If it's already downloaded it will be cached locally and just give a reference to the file without additional download
        Dependency adlDependency = project.getDependencies().create("org.adl:adl-bindist:" + spec.getVersion() + ":" + classifier + "@zip");
        Configuration adlDependencyConfig = project.getConfigurations().detachedConfiguration(adlDependency);
        adlDependencyConfig.setTransitive(false);

        try
        {
            Set<File> adlDistributionLocalArchives = adlDependencyConfig.resolve();

            //Should never return anything other than single file since that's how the dependency was defined
            //Bail out if we get anything else that is unexpected
            if (adlDistributionLocalArchives.size() != 1)
                throw new RuntimeException("Expected single dependency to download single file");

            return adlDistributionLocalArchives.iterator().next();
        }
        catch (ResolveException e)
        {
            throw new AdlDistributionNotFoundException("Failed to resolve ADL distribution " + adlDependency, e);
        }
    }

    private void unpackAdlDistribution(File adlDistZip, File targetDirectory)
    {
        fileSystemOperations.copy(copySpec -> {
           copySpec.from(archiveOperations.zipTree(adlDistZip))
                   .into(targetDirectory);
        });
    }

    /**
     * Returns a local directory containing a specific ADL distribution, downloading and installing it if necessary.
     *
     * @param spec the specification.
     *
     * @return the ADL distribution directory installed locally.
     *
     * @throws AdlDistributionNotFoundException when an ADL distribution is not available for the given spec.
     */
    public File adlDistribution(AdlDistributionSpec spec)
    throws AdlDistributionNotFoundException, IOException
    {
        //Attempt to locate an already-unpacked local version
        File adlInstallationDir = new File(adlBaseInstallationDirectory, specToInstallationDirectoryName(spec));
        if (adlInstallationDir.exists())
            return adlInstallationDir;

        //If it doesn't exist, download and unpack
        File adlDistZip = resolveAdlDistributionArchive(spec);

        //Use a temporary directory so other processes won't pick up a half-installed ADL distribution concurrently
        //and rename it to the proper name when done
        //Use nio instead of java.io.File because it has a createTempDirectory()
        Files.createDirectories(adlBaseInstallationDirectory.toPath());
        Path adlTempDir = Files.createTempDirectory(adlBaseInstallationDirectory.toPath(), "adlinstall-");
        unpackAdlDistribution(adlDistZip, adlTempDir.toFile());
        Files.move(adlTempDir, adlInstallationDir.toPath());

        return adlInstallationDir;
    }
}
