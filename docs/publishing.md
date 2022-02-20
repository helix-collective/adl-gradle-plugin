# Publishing

Documentation on Publishing to the Gradle Plugin Registry.

## TL;DR

Populate `.gradle/gradle.properties` with your credentials.

### Once Off
```
gradle.publish.key=XXX
gradle.publish.secret=YYY
```

### Pre-publish

```
gradle cleanFull
gradle check
```

### Publish

```
gradle publishPlugins
```
