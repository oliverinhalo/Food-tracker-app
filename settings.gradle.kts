pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
    }
}

rootProject.name = "food-tracker"

include(":app")

include(":core:model")
include(":core:common")
include(":core:ui")
include(":core:network")
include(":core:datastore")

include(":domain:recognition")
include(":data:recognition")

include(":feature:capture")
include(":feature:results")
include(":feature:home")
include(":feature:settings")
