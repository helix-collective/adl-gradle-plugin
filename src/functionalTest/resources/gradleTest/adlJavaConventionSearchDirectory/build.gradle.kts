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

sourceSets {
    main {
        adl {
            //TODO needed?
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    adlSearchDirectories(files("$projectDir/libadl"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

adl {
    version = "0.14"
    generations {
        java {
            javaPackage  = "adl.test"
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
        }
    }
}

tasks {
    clean {
        delete("$projectDir/generated")
    }

    processTestResources {
        from(sourceSets.main.get().allSource)
    }

    test {
        useJUnitPlatform()
    }

    register("runGradleTest") {
        dependsOn(check)
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
