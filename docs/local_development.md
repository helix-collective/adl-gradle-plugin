# Local Development

## Plugin Client

Create `settings.gradle.kts` file so the client look in the local maven repo before the `gradlePluginPortal`.

```
cat > settings.gradle.kts << EOF
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
EOF
```

After making change to the plugin & `publishToMavenLocal` use `gradlew build` as usual.

## Plugin Source

To hack on the plugin;
- make changes to the plugin code,
- run `gradle publishToMavenLocal` from the plugin directy.

```
./gradlew publishToMavenLocal
```
