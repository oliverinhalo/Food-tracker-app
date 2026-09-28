plugins {
    id("foodtracker.android.feature")
}

android {
    namespace = "dev.foodtracker.feature.capture"
}

dependencies {
    implementation(project(":domain:recognition"))
    implementation(project(":data:recognition"))

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.compose.material.icons.extended)
}
