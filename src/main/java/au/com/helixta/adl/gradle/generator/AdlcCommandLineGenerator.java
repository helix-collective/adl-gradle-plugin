package au.com.helixta.adl.gradle.generator;

import au.com.helixta.adl.gradle.config.AdlConfiguration;
import au.com.helixta.adl.gradle.config.GenerationConfiguration;
import au.com.helixta.adl.gradle.config.JavaGenerationConfiguration;
import au.com.helixta.adl.gradle.config.JavascriptGenerationConfiguration;
import au.com.helixta.adl.gradle.config.TypescriptGenerationConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates command lines for the ADL compiler.
 */
public class AdlcCommandLineGenerator
{
    /**
     * Generate the adlc command line arguments to generate files for the specified generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the generation configuration to generate files for.
     * @param fileSystemMapper the mapper to map paths to the target execution environment.
     *
     * @return a list of command line arguments for the adlc command, not including the adlc binary itself.
     *
     * @throws AdlGenerationException if an error occurs generating the command line.
     */
    public List<String> createAdlcCommand(AdlConfiguration adlConfiguration, GenerationConfiguration generation, FileSystemMapper fileSystemMapper)
    throws AdlGenerationException
    {
        if (generation instanceof JavaGenerationConfiguration)
            return adlcJavaCommand(adlConfiguration, (JavaGenerationConfiguration)generation, fileSystemMapper);
        else if (generation instanceof TypescriptGenerationConfiguration)
            return adlcTypescriptCommand(adlConfiguration, (TypescriptGenerationConfiguration)generation, fileSystemMapper);
        else if (generation instanceof JavascriptGenerationConfiguration)
            return adlcJavascriptCommand(adlConfiguration, (JavascriptGenerationConfiguration)generation, fileSystemMapper);
        else
            throw new AdlGenerationException("Unknown generation type: " + generation.getClass().getName());
    }

    /**
     * Generate the adlc command line arguments to generate Java files for the specified Java generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Java generation configuration to generate files for.
     * @param fileSystemMapper the mapper to map paths to the target execution environment.
     *
     * @return a list of command line arguments for the adlc command, not including the adlc binary itself.
     *
     * @throws AdlGenerationException if an error occurs generating the command line.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/backend-java.md">ADL Java Backend</a>
     */
    private List<String> adlcJavaCommand(AdlConfiguration adlConfiguration,
                                         JavaGenerationConfiguration generation,
                                         FileSystemMapper fileSystemMapper)
    throws AdlGenerationException
    {
        List<String> command = new ArrayList<>();
        command.add("java");

        command.add("--outputdir=" + fileSystemMapper.targetOutputDirectory(generation.getOutputDirectory().get()));

        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String targetSearchDir = fileSystemMapper.targetInputDirectory(searchDir);
            if (targetSearchDir != null)
                command.add("--searchdir=" + fileSystemMapper.targetInputDirectory(searchDir));
        }

        if (generation.getJavaPackage() != null && !generation.getJavaPackage().trim().isEmpty())
            command.add("--package=" + generation.getJavaPackage());

        if (adlConfiguration.isVerbose())
            command.add("--verbose");

        if (generation.isGenerateAdlRuntime())
            command.add("--include-rt");
        if (generation.getAdlRuntimePackage() != null && !generation.getAdlRuntimePackage().isEmpty())
            command.add("--rtpackage=" + generation.getAdlRuntimePackage());

        if (generation.isGenerateTransitive())
            command.add("--generate-transitive");
        if (generation.getSuppressWarningsAnnotation() != null && !generation.getSuppressWarningsAnnotation().isEmpty())
            command.add("--suppress-warnings-annotation=" + generation.getSuppressWarningsAnnotation());
        if (generation.getHeaderComment() != null && !generation.getHeaderComment().isEmpty())
            command.add("--header-comment=" + generation.getHeaderComment());

        if (generation.getManifest().isPresent())
            command.add("--manifest=" + fileSystemMapper.targetFile(generation.getManifest().get()));

        command.addAll(generation.getCompilerArgs());

        command.addAll(fileSystemMapper.targetFiles(new FileSystemMapper.LabelledFileTree(AdlFileTreeLabel.SOURCES, adlConfiguration.getSource())));

        return command;
    }

    /**
     * Generate the adlc command line arguments to generate Typescript files for the specified Typescript generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Typescript generation configuration to generate files for.
     * @param fileSystemMapper the mapper to map paths to the target execution environment.
     *
     * @return a list of command line arguments for the adlc command, not including the adlc binary itself.
     *
     * @throws AdlGenerationException if an error occurs generating the command line.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/backend-typescript.md">ADL Typescript command</a>
     */
    private List<String> adlcTypescriptCommand(AdlConfiguration adlConfiguration,
                                               TypescriptGenerationConfiguration generation,
                                               FileSystemMapper fileSystemMapper)
    throws AdlGenerationException
    {
        List<String> command = new ArrayList<>();
        command.add("typescript");

        command.add("--outputdir=" + fileSystemMapper.targetOutputDirectory(generation.getOutputDirectory().get()));

        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String targetSearchDir = fileSystemMapper.targetInputDirectory(searchDir);
            if (targetSearchDir != null)
                command.add("--searchdir=" + fileSystemMapper.targetInputDirectory(searchDir));
        }

        if (adlConfiguration.isVerbose())
            command.add("--verbose");

        if (generation.isGenerateTransitive())
            command.add("--generate-transitive");
        if (generation.isGenerateResolver())
            command.add("--include-resolver");
        if (!generation.isGenerateAst())
            command.add("--exclude-ast");

        if (generation.isGenerateAdlRuntime())
            command.add("--include-rt");
        if (generation.getRuntimeModuleName() != null)
            command.add("--runtime-dir=" + generation.getRuntimeModuleName());

        if (generation.getManifest().isPresent())
            command.add("--manifest=" + fileSystemMapper.targetFile(generation.getManifest().get()));

        command.addAll(generation.getCompilerArgs());

        command.addAll(fileSystemMapper.targetFiles(new FileSystemMapper.LabelledFileTree(AdlFileTreeLabel.SOURCES, adlConfiguration.getSource())));

        return command;
    }

    /**
     * Generate the adlc command line arguments to generate Javascript files for the specified Javascript generation configuration.
     *
     * @param adlConfiguration the top-level ADL configuration.
     * @param generation the Javascript generation configuration to generate files for.
     * @param fileSystemMapper the mapper to map paths to the target execution environment.
     *
     * @return a list of command line arguments for the adlc command, not including the adlc binary itself.
     *
     * @throws AdlGenerationException if an error occurs generating the command line.
     *
     * @see <a href="https://github.com/timbod7/adl/blob/master/docs/compiler.md">ADL Javascript command</a>
     */
    private List<String> adlcJavascriptCommand(AdlConfiguration adlConfiguration,
                                               JavascriptGenerationConfiguration generation,
                                               FileSystemMapper fileSystemMapper)
    throws AdlGenerationException
    {
        List<String> command = new ArrayList<>();
        command.add("javascript");

        command.add("--outputdir=" + fileSystemMapper.targetOutputDirectory(generation.getOutputDirectory().get()));

        for (File searchDir : adlConfiguration.getSearchDirectories())
        {
            String targetSearchDir = fileSystemMapper.targetInputDirectory(searchDir);
            if (targetSearchDir != null)
                command.add("--searchdir=" + fileSystemMapper.targetInputDirectory(searchDir));
        }

        if (adlConfiguration.isVerbose())
            command.add("--verbose");

        if (generation.getManifest().isPresent())
            command.add("--manifest=" + fileSystemMapper.targetFile(generation.getManifest().get()));

        command.addAll(generation.getCompilerArgs());

        command.addAll(fileSystemMapper.targetFiles(new FileSystemMapper.LabelledFileTree(AdlFileTreeLabel.SOURCES, adlConfiguration.getSource())));

        return command;
    }
}
