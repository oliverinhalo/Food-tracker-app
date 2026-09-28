plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.foodtracker.core.network"

    buildTypes {
        debug {
            buildConfigField("boolean", "VERBOSE_HTTP_LOGS", "true")
        }
        release {
            buildConfigField("boolean", "VERBOSE_HTTP_LOGS", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.okhttp.core)
    api(libs.retrofit.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
}
