import au.com.helixta.adl.gradle.AdlGenerateTask

buildscript {
   repositories {
       mavenLocal()
       mavenCentral()
   }
}

plugins {
    `java-library`
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
    testImplementation("com.google.guava:guava:30.1-jre")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

val adlJava = tasks.register<AdlGenerateTask>("adlJava") {
    version = "0.14"
    source(file("$projectDir/src/main/adl"))
    isVerbose = true
    generations {
        java {
            javaPackage  = "adl.test"
            outputDirectory.set(file("$projectDir/generated/java"))
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
            manifest.set(file("$projectDir/generated/manifest/adl-java"))
        }
        typescript {
            outputDirectory.set(file("$projectDir/generated/typescript"))
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
            isGenerateResolver = true
            manifest.set(file("$projectDir/generated/manifest/adl-typescript"))
        }
    }
}

sourceSets["main"].java {
    srcDir("$projectDir/generated/java")
}

sourceSets["test"].resources {
    srcDir("$projectDir/generated/manifest")
}

tasks {
    compileJava {
        dependsOn(adlJava)
    }

    clean {
        delete("$projectDir/generated")
    }

    register("runGradleTest") {
        dependsOn(compileJava)
        dependsOn(test)
    }
}
