# ADL Gradle Plugin
 
A Gradle plugin for generating source code from ADL definitions using the 
[ADL language](https://github.com/timbod7/adl).

## Requirements

The ADL Gradle plugin requires Gradle 6.8 or later.  It does not require the ADL
tool to exist on your system - an appropriate version for the build will be downloaded
and cached in the Gradle build cache as needed.

ADL binaries only exist for Linux and MacOS x86-64.  For other platforms, [Docker](https://www.docker.com/) can
be used to execute ADL - Docker will need to be installed for the plugin to work on
these systems, such as Windows platforms.  The Docker instance can be remote - file system mapping 
is not required.

Without explicit configuration, the plugin will use the native binaries if the 
current platform supports it, otherwise it will try to use Docker.

## Basic Usage

Add the Gradle plugin to your project, for example (Kotlin build script):

```
plugins {
    ...
    id("au.com.helixta.adl") version("0.0.1")
}
```

then define some basic ADL properties in your build script:

```
adl {
    version = "0.14"
    generations {
        java {
            javaPackage  = "adl.test"
        }
    }
}
```

Most things work with sensible defaults, however two things must be defined:

- the version of ADL to use (see [ADL releases](https://github.com/timbod7/adl/releases) for available versions)
- what source code to generate - currently Java, Javascript and Typescript are supported
  from the Gradle plugin

Everything else may be optionally configured.  In the example, the Java package that will be used
for the generated code is defined instead of using the default.

Your ADL source code, by default, goes in the directory `src/main/adl`.
This may be reconfigured using source sets.  Generated source code is placed by 
default into `build/generated/sources/adl` but may also be reconfigured.  The ADL plugin will not compile Java or Typescript code itself, you will need to use
other Gradle plugins to do this.  It will however configure appropriate tasks for generating
ADL and has support for wiring everything up for Java builds automatically so
ADL task dependencies do not need to be explicitly defined in the build script to make the 
Java compiler pick up source code.

# Configuration

## Source Sets

When using the Java plugin in your build, for every [source set](https://docs.gradle.org/current/userguide/java_plugin.html#source_sets) defined in the project 
('main', 'test', etc.) an ADL source directory will be configured with the name 'adl'.  So 
for standard Java projects you will get `src/main/adl` and `src/test/adl`.  By default, all ADL
source set directories will be configured with an inclusion pattern of '**/*.adl'.  This can be 
reconfigured if needed in your build file:

```
sourceSets {
    main {
        adl {
            //Exclude any ADL files starting with 'x'
            exclude("**/x*")
        }
    }
}
```

Dependencies are handled similarly to how the Java plugin works - e.g. ADL files from the 'main' 
source set are available from the 'test' source set.

## Platform

The platform controls whether the ADL tool is executed natively on the OS or in a Docker container.
By default (auto), native execution will be used on platforms that ADL has native binaries for,
and Docker everywhere else.  This can be configured and customized with the `platform` option:

```
adl {
    ...
    platform = au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform.DOCKER
}
```

This will force the use of Docker everywhere.

Docker can be further configured in the build file itself or through environment variables.
See the [Docker-java documentation](https://github.com/docker-java/docker-java/blob/master/docs/getting_started.md)
for how this configuration is picked up through environment variables and other configuration if
not explicitly configured in the build file.

Explicit build configuration takes precedence, however:

```
adl {
    ...
    //Force using Docker on a a specific host for this build
    platform = au.com.helixta.adl.gradle.containerexecutor.ExecutionPlatform.DOCKER
    docker {
        host = uri("tcp://192.168.99.100:2376")
        imageBuildTimeout = `java.time`.Duration.ofMinutes(10)
        imagePullTimeout = `java.time`.Duration.ofMinutes(10)
    }
}

```

# Generations

A generation generates source code in a target language from ADL source.

Currently 3 types of generation can be used.  See the ADL documentation and use autocomplete
to discover configuration options.

## Java

Generates [Java](https://github.com/timbod7/adl/blob/master/docs/backend-java.md) source code from ADL.

```
adl {
    version = "0.14"
    generations {
        java {
            javaPackage  = "myproject.adl"
            isGenerateTransitive = true
            isGenerateAdlRuntime = true
            headerComment = "Generated from ADL Gradle Plugin"
        }
    }
}

```

## Typescript

Generates [Typescript](https://github.com/timbod7/adl/blob/master/docs/backend-typescript.md) code from ADL.

```
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
```

In this example, because the Java plugin conventions are not used (it's a typescript project)
the task is registered manually.

## Javascript 

Generates Javascript code from ADL.

```
adl {
    version = "0.14"
    generations {
        javascript {
        }
    }
}
```

In this example, we're assuming a Java/Javascript hybrid Gradle project.

## Custom compiler arguments

Most ADL compiler arguments are available in the ADL Gradle plugin's model, however there might be 
newer versions of ADL that support new features that the model doesn't have yet.  For these cases,
the `compilerArgs` option:

```
adl {
    version = "0.14"
    generations {
        java {
            javaPackage  = "myproject.adl"
            compilerArgs.add("--parcelable")
        }
    }
}
```

# Dependency Handling

Search directories for importing additional ADL source files for processing in your project can be added
through Gradle's dependency mechanism using `adlSearchDirectories` dependency configurations.  They can exist
in a directory in the filesystem or as an archive such as a JAR - the plugin will extract them as needed
for processing with the ADL compiler.

For example:

```
dependencies {
    adlSearchDirectories(files("$projectDir/libadl"))
}
```

will add all ADL files in the 'libadl' directory as search directories in the ADL compiler.

It is also possible to use standard artifacts in a repository when they contain ADL files in 
TAR or ZIP (or zip-derivative such as JAR) files.  Refer to these like normal Gradle dependencies.

`adlSearchDirectories` configurations are available for each source set in the project.  For
'main', use `adlSearchDirectories`, for 'test', use `testAdlSearchDirectories` and in the generic
case use `<sourcesetname>AdlSearchDirectories`.

