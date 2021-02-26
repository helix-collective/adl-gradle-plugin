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
            //Exclude any ADL files starting with 'd'
            exclude("**/d*")
        }
    }
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

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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
