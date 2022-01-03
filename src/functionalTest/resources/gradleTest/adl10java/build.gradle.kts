buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins {
    `java-library`
    id("au.net.causal.adl")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

adl {
    version = "1.0"
    generations {
        java {
            javaPackage  = "adl.test"
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
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
        platform = au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform.valueOf(it)
    }
}

//For testing, force rebuild the image every time to ensure we are testing the Docker image build logic
tasks.withType<au.com.helixta.adl.gradle.AdlGenerateTask> {
    docker.imageBuildMode = au.com.helixta.adl.gradle.config.ImageBuildMode.REBUILD
}
