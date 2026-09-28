package dev.foodtracker.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 37
internal const val MIN_SDK = 26

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Shared Android config applied to both the application and every library module, so the SDK
 * levels, Java level and desugaring never drift apart between modules.
 *
 * Note this configures [CommonExtension] through property access rather than the familiar
 * `defaultConfig { }` block syntax: AGP 9 removed the lambda-taking overloads from the common
 * interface, keeping them only on the concrete application/library extensions.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = COMPILE_SDK
    extension.defaultConfig.minSdk = MIN_SDK

    extension.compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is available from API 26, but desugaring keeps newer JDK APIs open to us.
        isCoreLibraryDesugaringEnabled = true
    }

    extension.testOptions.unitTests.apply {
        isIncludeAndroidResources = true
        isReturnDefaultValues = true
    }

    // AGP 9 has built-in Kotlin support and derives the Kotlin jvmTarget from
    // compileOptions.targetCompatibility above, so there is no separate Kotlin block to configure.

    dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())

    configureTests()
}

/**
 * Gradle 9 fails a test task that has a test source set but discovers no tests in it. Several
 * modules here are pure wiring with nothing worth unit-testing, and an empty module should not
 * break the build for the ones that do have tests.
 */
internal fun Project.configureTests() {
    tasks.withType(Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
