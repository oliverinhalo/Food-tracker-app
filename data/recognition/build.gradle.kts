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
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(project(":domain:nutrition"))
    implementation(libs.mlkit.objectdetection)
    implementation(libs.tflite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
