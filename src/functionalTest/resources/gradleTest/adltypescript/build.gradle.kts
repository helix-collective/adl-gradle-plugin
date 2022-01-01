import au.com.helixta.adl.gradle.AdlGenerateTask

buildscript {
   repositories {
       mavenLocal()
       mavenCentral()
       gradlePluginPortal()
   }
}

plugins {
    `java-library`
    id("au.com.helixta.adl")
    id("com.github.node-gradle.node") version("3.1.0")
}

node {
    version.set("14.15.4")
    download.set(true)
}

val adlTypescript = tasks.register<AdlGenerateTask>("adlTypescript") {
    version = "0.14"
    source(file("$projectDir/src/main/adl"))
    isVerbose = true
    generations {
        typescript {
            outputDirectory.set(file("$projectDir/generated"))
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
            isGenerateResolver = true
        }
    }
}

val tsTest = tasks.register<com.github.gradle.node.npm.task.NpmTask>("tstest") {
    dependsOn(adlTypescript)
    dependsOn(tasks.npmInstall)
    args.set(listOf("test"))
}

tasks.register<Delete>("cleanfull") {
    group = "build"
    description = "Deletes build directory and node modules"
    dependsOn(tasks.clean)
    delete("$projectDir/node_modules")
}

tasks {
    clean {
        delete("$projectDir/generated")
    }

    check {
        dependsOn(tsTest)
    }

    register("runGradleTest") {
        dependsOn(check)
    }
}

//If adl.platform system property is configured, use it to configure platform of ADL tasks
System.getProperty("adl.platform")?.let {
    tasks.withType<au.com.helixta.adl.gradle.AdlGenerateTask> {
        platform = au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform.valueOf(it)
    }
}

//For testing, force rebuild the image every time to ensure we are testing the Docker image build logic
tasks.withType<au.com.helixta.adl.gradle.AdlGenerateTask> {
    docker.imageBuildMode = au.com.helixta.adl.gradle.config.ImageBuildMode.REBUILD
}
