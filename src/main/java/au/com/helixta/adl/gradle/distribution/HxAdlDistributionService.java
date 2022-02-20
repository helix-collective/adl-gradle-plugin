package au.com.helixta.adl.gradle.distribution;

import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.nativeplatform.platform.internal.Architectures;

import java.net.URI;

public class HxAdlDistributionService extends AbstractDistributionService
{
    public HxAdlDistributionService(GradleUserHomeDirProvider homeDirProvider,
                                    FileSystemOperations fileSystemOperations,
                                    ArchiveOperations archiveOperations, Project project)
    {
        super(URI.create("https://github.com/helix-collective/helix-adl-tools/releases/download/"), "hxadl", homeDirProvider, fileSystemOperations,
              archiveOperations, project);
    }

    @Override
    protected DownloadParameters specifierToDownloadParameters(DistributionSpecifier specifier)
    {
        String classifier = specToClassifier(specifier);
        if (classifier == null)
            return null;
        return new DownloadParameters("au.com.helix.adl.tools", "hxadl-bindist", classifier, "zip");
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
}
