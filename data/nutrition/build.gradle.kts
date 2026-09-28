import java.io.FileInputStream
import java.util.Properties

plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.foodtracker.data.nutrition"

    defaultConfig {
        // Build-time key from local.properties (gitignored) or the environment, so CI can inject
        // it without a file. Absent in a fresh clone, in which case UsdaClient reports itself
        // unconfigured and Open Food Facts carries the lookups on its own.
        val usdaKey = System.getenv("USDA_API_KEY")
            ?: rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.let { Properties().apply { FileInputStream(it).use(::load) } }
                ?.getProperty("USDA_API_KEY")
            ?: ""
        buildConfigField("String", "USDA_API_KEY", "\"$usdaKey\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":domain:nutrition"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)

    testImplementation(libs.okhttp.mockwebserver)
}
