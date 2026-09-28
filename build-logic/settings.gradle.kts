dependencyResolutionManagement {
    repositories {
        google()
        // Google's Maven Central mirror. Functionally identical to mavenCentral() but does not
        // rate-limit parallel dependency resolution, which repo.maven.apache.org does aggressively
        // from shared/CI egress IPs. mavenCentral() stays below it as the fallback.
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
            content { includeGroupByRegex(".*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
