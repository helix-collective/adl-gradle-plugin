package au.com.helixta.adl.gradle.distribution;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public interface DistributionService
{
    /**
     * Resolves a distribution archive, downloading it from the distribution release page into the
     * local repository if not already downloaded.
     * <p>
     *
     * This will attempt to resolve a distribution for the given spec.  This might not successfully find a distribution
     * for the given version, OS and platform, in which case a DistributionNotFoundException is thrown.  Otherwise,
     * the distribution will be resolved by Gradle into the local repository, potentially downloading from a remote
     * site or using an existing cached version of the archive in the local repository.  Distribution archives are
     * often ZIP or TAR files, but the format will be up to the implementation of the distribution service and what is
     * available.
     *
     * @param spec the distribution specification.
     *
     * @return a local file of the distribution archive.
     *
     * @throws DistributionNotFoundException if no distribution is available for the given spec.
     */
    public File resolveDistributionArchive(DistributionSpecifier spec)
    throws DistributionNotFoundException;

    /**
     * Resolves and unpacks a distribution into a local distribution cache directory.  This will resolve a distribution
     * archive and then unpack that archive into a well-known local directory used for caching unpacked distributions.
     * <p>
     *
     * This might download a distribution if it is not already downloaded into the local repository.
     *
     * @param spec the distribution specification.
     *
     * @return a directory containing the unpacked distribution.
     *
     * @throws DistributionNotFoundException if no distribution is available for the given spec.
     * @throws IOException if an I/O error occurs processing or unpacking the distribution archive.
     */
    public File resolveDistribution(DistributionSpecifier spec)
    throws DistributionNotFoundException, IOException;
}
