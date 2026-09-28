plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
}

android {
    namespace = "dev.foodtracker.data.diary"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":domain:nutrition"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
