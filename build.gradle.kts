plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.ysb33r.gradletest") version("2.0")
}

group = "au.com.helixta.adl.gradle"
version = "0.0.1"

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("adlPlugin") {
            id = "au.com.helixta.adl"
            implementationClass = "au.com.helixta.adl.gradle.AdlGradlePlugin"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("com.github.docker-java:docker-java-core:3.2.7")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.2.7")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testImplementation("com.github.javaparser:javaparser-core:3.18.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    gradleTest("com.google.code.gson:gson:2.8.6")
}

tasks {
    test {
        useJUnitPlatform()
    }

    abstract class AdlPlatformTestGenerator(val adlPlatform: String) : org.ysb33r.gradle.gradletest.TestGenerator() {
        override fun getOutputDir(): File {
            return super.getOutputDir().resolveSibling("adl${adlPlatform}")
        }

        override fun getGradleArguments(): MutableList<String> {
            return (super.getGradleArguments() + "-Dadl.platform=${adlPlatform.toUpperCase()}").toMutableList()
        }
    }

    abstract class AdlNativeTestGenerator : AdlPlatformTestGenerator("native");
    abstract class AdlDockerTestGenerator : AdlPlatformTestGenerator("docker");

    //Generate native and docker tests from the originals, explicitly configuring ADL with each platform
    val dockerGradleTestGenerator = register<AdlDockerTestGenerator>("dockerGradleTestGenerator") {
        linkedTestTaskName = gradleTestGenerator.get().linkedTestTaskName
        testPackageName = "adldocker." + gradleTestGenerator.get().testPackageName
    }
    gradleTestGenerator {
        dependsOn(dockerGradleTestGenerator)
        doLast {
            copy {
                from(dockerGradleTestGenerator.get().outputDir)
                into("$outputDir/adldocker")
            }
        }
    }

    //Native ADL platform is only available on Mac and Linux, so don't try to run these on other platforms
    val performNativePlatformTests = with(org.gradle.internal.os.OperatingSystem.current()) { isLinux || isMacOsX }
    if (performNativePlatformTests) {
        val nativeGradleTestGenerator = register<AdlNativeTestGenerator>("nativeGradleTestGenerator") {
            linkedTestTaskName = gradleTestGenerator.get().linkedTestTaskName
            testPackageName = "adlnative." + gradleTestGenerator.get().testPackageName
        }
        gradleTestGenerator {
            dependsOn(nativeGradleTestGenerator)
            doLast {
                copy {
                    from(nativeGradleTestGenerator.get().outputDir)
                    into("$outputDir/adlnative")
                }
            }
        }
    } else {
        gradleTestGenerator {
            doFirst {
                logger.warn("This OS/platform does not support native ADL execution, so cannot run the ADL native integration tests")
            }
        }
    }

    gradleTest {
        versions("6.8")
        dependsOn(jar)
        kotlinDsl = true

        //Only include the two derived generators' files, not the originals
        //originals would execute in adl platform=auto mode but since
        //we are hitting both docker/native modes already that would just be
        //duplicate effort for no gain
        include("adlnative/**", "adldocker/**")

        //Print names of tests before they run
        beforeTest(closureOf<TestDescriptor>{
            logger.lifecycle("\t${this.name}")
        })
    }

    javadoc {
        options.outputLevel = JavadocOutputLevel.QUIET
        options.quiet()
        if (options is StandardJavadocDocletOptions) {
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    // Make source files available as resources to tests
    processTestResources {
        from(sourceSets.main.get().allSource)
    }
}

//Workaround for https://gitlab.com/ysb33rOrg/gradleTest/-/issues/120
configurations.runtime.get().dependencies.addAll(configurations.runtimeClasspath.get().allDependencies)
