import au.com.helixta.adl.gradle.AdlGenerateTask

buildscript {
   repositories {
       mavenLocal()
       mavenCentral()
   }
}

plugins {
    `java-library`
    id("com.github.node-gradle.node") version("2.2.4")
}

node {
    version = "14.15.4"
    download = true
}

val adlTypescript = tasks.register<AdlGenerateTask>("adlTypescript") {
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

val tsTest = tasks.register<com.moowork.gradle.node.npm.NpmTask>("tstest") {
    dependsOn(adlTypescript)
    dependsOn(tasks.npmInstall)
    setArgs(listOf("test"));
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
