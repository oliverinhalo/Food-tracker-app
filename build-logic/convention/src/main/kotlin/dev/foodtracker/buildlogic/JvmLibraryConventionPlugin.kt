package dev.foodtracker.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure-Kotlin modules (domain, model): no Android dependency, fast unit tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        // javac and kotlinc must agree on the bytecode level or Gradle's JVM-target consistency
        // check fails. Pinning both to 17 (rather than requesting a 17 toolchain) means the build
        // runs on whatever recent JDK is installed, instead of needing that exact one present.
        extensions.getByType<JavaPluginExtension>().apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        extensions.getByType<KotlinJvmProjectExtension>().compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        configureTests()

        dependencies {
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
