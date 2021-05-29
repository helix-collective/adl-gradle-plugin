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

adl {
    version = "0.14"
    generations {
        javascript {
        }
    }
}

//For JUnit testing, add generated Javascript code as test resources
sourceSets["test"].resources {
    srcDir("$projectDir/build/generated/sources/adl/javascript")
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    testImplementation("com.google.guava:guava:30.1-jre")
    testImplementation("org.graalvm.js:js:21.0.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    clean {
        delete("$projectDir/generated")
    }

    compileTestJava {
        dependsOn(generateAdl)
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
