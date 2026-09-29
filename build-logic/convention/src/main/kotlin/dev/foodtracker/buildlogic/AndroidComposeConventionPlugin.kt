package dev.foodtracker.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            val bom = libs.findLibrary("compose-bom").get()
            dependencies {
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-ui-graphics").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            }

            extensions.getByType<ComposeCompilerGradlePluginExtension>().apply {
                // Our domain models are immutable but live in modules without the Compose runtime,
                // so they cannot be annotated. Telling the compiler explicitly is what lets item
                // rows skip recomposition.
                stabilityConfigurationFiles.add(
                    isolated.rootProject.projectDirectory.file("compose-stability.conf"),
                )

                // `-Pfoodtracker.composeReports=true` writes stability and recomposition reports.
                if (providers.gradleProperty("foodtracker.composeReports").isPresent) {
                    val dir = layout.buildDirectory.dir("compose-reports")
                    reportsDestination.set(dir)
                    metricsDestination.set(dir)
                }
            }
        }
    }
}
