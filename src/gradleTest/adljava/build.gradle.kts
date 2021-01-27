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
    testImplementation("com.github.javaparser:javaparser-core:3.18.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

val adlJava = tasks.register<AdlGenerateTask>("adlJava") {
    sourcepath(file("$projectDir/src/main/adl"))
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
