plugins {
    `java-gradle-plugin`
    `maven-publish`
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

tasks.jar {
    manifest {
        attributes(
            "Implementation-Version" to archiveVersion
        )
    }
}

open class GradleFunctionalTest : Test() {
    @Option(option = "gradletest", description = "Only run functional tests whose project names match this regexp pattern.")
    fun setGradletest(gradletest: String) {
        systemProperty("gradletest", gradletest)
    }

    fun gradleTestFilteringUsed(): Boolean {
        return systemProperties.get("gradletest") != null
    }
}

val functionalTest = sourceSets.create("functionalTest")
val functionalTestTask = tasks.register<GradleFunctionalTest>("functionalTest") {
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("test.projectworkspace.directory", layout.buildDirectory.dir("functest").get().asFile.path)
    systemProperty("test.testkit.directory", layout.projectDirectory.dir("build-home").asFile.path)
    outputs.dir(layout.buildDirectory.dir("functest"))
    workingDir(layout.buildDirectory.dir("functest"))

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        //Print names of tests after they run
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.resultType != TestResult.ResultType.SKIPPED || gradleTestFilteringUsed()) {
                logger.lifecycle("\t${result.resultType} - ${testDescriptor.className?.substringAfterLast(".")} : ${testDescriptor.displayName}")
            }
        }
        //Print suite class names that are full of skipped tests that indicate they contain tests that cannot run on this platform
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (!gradleTestFilteringUsed()) {
                if (suite.className != null && result.skippedTestCount > 0) {
                    logger.warn("\tIntegration tests in ${suite.name} cannot run on this platform - they are being skipped.")
                }
            }
        }
    })
}

dependencies {
    implementation("com.github.docker-java:docker-java-core:3.2.7")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.2.7")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api:5.7.1")
    "functionalTestImplementation"("org.assertj:assertj-core:3.18.1")
    "functionalTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "functionalTestImplementation"("io.github.classgraph:classgraph:4.8.104")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testImplementation("com.github.javaparser:javaparser-core:3.18.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.register<Delete>("cleanFull") {
    dependsOn(tasks.clean)
    description = "Deletes the build directory and the functional test workspace home directory"
    group = "build"
    delete("build-home")
}

tasks {
    test {
        useJUnitPlatform()
    }

    check {
        dependsOn(functionalTestTask)
    }

    gradlePlugin {
        testSourceSets(functionalTest)
    }

    javadoc {
        options.outputLevel = JavadocOutputLevel.QUIET
        options.quiet()
        if (options is StandardJavadocDocletOptions) {
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}
