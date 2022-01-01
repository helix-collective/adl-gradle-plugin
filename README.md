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


