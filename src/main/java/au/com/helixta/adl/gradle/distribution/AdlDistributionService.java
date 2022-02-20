package au.com.helixta.adl.gradle.distribution;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.nativeplatform.platform.internal.Architectures;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class AdlDistributionService extends AbstractDistributionService
{
    private static final String BASE_DISTRIBUTION_GROUP_ID = "org.adl.adlc";
    private static final String DISTRIBUTION_SIMPLE_NAME = "adl";

    private final Project project;

    public AdlDistributionService(GradleUserHomeDirProvider homeDirProvider,
                                  FileSystemOperations fileSystemOperations,
                                  ArchiveOperations archiveOperations, Project project)
    {
        super(URI.create("https://github.com/timbod7/adl/releases/download/"), DISTRIBUTION_SIMPLE_NAME, homeDirProvider, fileSystemOperations,
              archiveOperations, project);
        this.project = Objects.requireNonNull(project);
    }

    @Override
    public URI resolveDistributionBaseUrl(DistributionSpecifier spec)
    {
        //Special case for Helix-specific versions - [github-repo:version]
        ParsedVersion parsedVersion = ParsedVersion.parse(spec.getVersion());
        if (parsedVersion != null)
            return URI.create("https://github.com/" + parsedVersion.getGitHubRepositoryName() + "/adl/releases/download/");
        else
            return super.resolveDistributionBaseUrl(spec);
    }

    @Override
    protected Dependency createDownloadDependency(DownloadParameters downloadParameters, DistributionSpecifier spec)
    {
        //Special case for Helix-specific versions
        ParsedVersion parsedVersion = ParsedVersion.parse(spec.getVersion());
        if (parsedVersion != null)
        {
            return project.getDependencies().create(
                    downloadParameters.getGroupId()
                    + ":" + downloadParameters.getArtifactId() + ":" + parsedVersion.getVersion()
                    + ":" + downloadParameters.getClassifier() + "@" + downloadParameters.getExtension());
        }
        else
            return super.createDownloadDependency(downloadParameters, spec);
    }

    @Override
    protected String specToInstallationDirectoryName(DistributionSpecifier spec)
    {
        ParsedVersion parsedVersion = ParsedVersion.parse(spec.getVersion());
        if (parsedVersion != null)
        {
            return DISTRIBUTION_SIMPLE_NAME + "-" + parsedVersion.getGitHubRepositoryName()
                    + "-" + parsedVersion.getVersion()
                    + "-" + osFamilyName(spec.getOs())
                    + "-" + spec.getArchitecture().getName().toLowerCase(Locale.ROOT);
        }
        else
            return super.specToInstallationDirectoryName(spec);
    }

    @Override
    protected DownloadParameters specifierToDownloadParameters(DistributionSpecifier specifier)
    {
        String classifier = specToClassifier(specifier);
        if (classifier == null)
            return null;

        ParsedVersion parsedVersion = ParsedVersion.parse(specifier.getVersion());
        String groupId = BASE_DISTRIBUTION_GROUP_ID;
        if (parsedVersion != null)
            groupId = groupId + "." + parsedVersion.getGitHubRepositoryName();

        return new DownloadParameters(groupId, "adl-bindist", classifier, "zip");
    }

    /**
     * For a given spec, make an archive classifier for downloading off of the ADL Github releases page.
     */
    private String specToClassifier(DistributionSpecifier spec)
    {
        //Only 64-bit releases are available for ADL
        if (!Architectures.X86_64.isAlias(spec.getArchitecture().getName()))
            return null;

        //Only OSX/linux supported
        if (spec.getOs().isMacOsX() || spec.getOs().isLinux())
            return osFamilyName(spec.getOs());

        //No other OSes supported
        return null;
    }

    private static class ParsedVersion
    {
        private final String gitHubRepositoryName;
        private final String version;

        public ParsedVersion(String gitHubRepositoryName, String version)
        {
            this.gitHubRepositoryName = gitHubRepositoryName;
            this.version = version;
        }

        public static ParsedVersion parse(String version)
        {
            if (version.contains(":"))
            {
                String[] versionParts = version.split(Pattern.quote(":"), 2);
                String repoName = versionParts[0];
                String parsedVersion = versionParts[1];
                return new ParsedVersion(repoName, parsedVersion);
            }
            else
                return null;
        }

        public String getGitHubRepositoryName()
        {
            return gitHubRepositoryName;
        }

        public String getVersion()
        {
            return version;
        }
    }
}
