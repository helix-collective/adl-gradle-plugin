import au.com.helixta.adl.gradle.AdlGenerateTask

buildscript {
   repositories {
       mavenLocal()
       mavenCentral()
   }
}

plugins {
    `java-library`
    id("au.com.helixta.adl")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testImplementation("com.github.javaparser:javaparser-core:3.18.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

val adlCreateSearchDirArchive = tasks.register<Zip>("adlCreateSearchDirArchive") {
    from("$projectDir/libadl")
    archiveFileName.set("libadl.zip")
    destinationDirectory.set(file("$buildDir/libadl"))
}

val adlJava = tasks.register<AdlGenerateTask>("adlJava") {
    version = "0.14"
    source(file("$projectDir/src/main/adl"))
    searchDirectory(file("$buildDir/libadl/libadl.zip"))
    isVerbose = true
    generations {
        java {
            javaPackage  = "adl.test"
            outputDirectory.set(file("$projectDir/generated/java"))
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
            headerComment = """
                Full
                of
                galahs
            """.trimIndent()
        }
    }
    dependsOn(adlCreateSearchDirArchive)
}

sourceSets["main"].java {
    srcDir("$projectDir/generated/java")
}

tasks {
    compileJava {
        dependsOn(adlJava)
    }

    clean {
        delete("$projectDir/generated")
    }

    processTestResources {
        from(sourceSets.main.get().allSource)
    }

    register("runGradleTest") {
        dependsOn(compileJava)
        dependsOn(test)
    }
}

//If adl.platform system property is configured, use it to configure platform of ADL tasks
System.getProperty("adl.platform")?.let {
    tasks.withType<au.com.helixta.adl.gradle.AdlGenerateTask> {
        platform = au.com.helixta.adl.gradle.config.AdlPlatform.valueOf(it)
    }
}

//For testing, force rebuild the image every time to ensure we are testing the Docker image build logic
tasks.withType<au.com.helixta.adl.gradle.AdlGenerateTask> {
    docker.imageBuildMode = au.com.helixta.adl.gradle.config.ImageBuildMode.REBUILD
}
