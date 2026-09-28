plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
}

android {
    namespace = "dev.foodtracker.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
