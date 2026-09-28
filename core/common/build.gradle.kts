plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
}

android {
    namespace = "dev.foodtracker.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
