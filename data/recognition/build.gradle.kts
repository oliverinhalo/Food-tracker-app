plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.foodtracker.data.recognition"
}

dependencies {
    api(project(":domain:recognition"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
