plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.compose")
}

android {
    namespace = "dev.foodtracker.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
