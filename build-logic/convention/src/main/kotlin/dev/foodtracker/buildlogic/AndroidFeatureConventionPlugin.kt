package dev.foodtracker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a `:feature:*` module always needs: Compose, Hilt, the design system and the
 * lifecycle/navigation glue. Keeps feature build files down to their own dependencies.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("foodtracker.android.library")
        pluginManager.apply("foodtracker.android.compose")
        pluginManager.apply("foodtracker.android.hilt")

        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))

            add("implementation", libs.findLibrary("androidx-core-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())

            add("testImplementation", libs.findLibrary("turbine").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
            add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
        }
    }
}
