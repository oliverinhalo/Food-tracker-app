plugins {
    id("foodtracker.android.feature")
}

android {
    namespace = "dev.foodtracker.feature.home"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":data:diary"))
    implementation(project(":data:nutrition"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
