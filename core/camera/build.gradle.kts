plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.compose")
}

android {
    namespace = "dev.foodtracker.core.camera"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.compose.material.icons.extended)
}
