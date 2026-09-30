plugins {
    id("foodtracker.android.feature")
}

android {
    namespace = "dev.foodtracker.feature.settings"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":data:diary"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material.icons.extended)
}
