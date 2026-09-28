plugins {
    id("foodtracker.android.feature")
}

android {
    namespace = "dev.foodtracker.feature.results"
}

dependencies {
    implementation(project(":domain:recognition"))
    implementation(project(":data:recognition"))
    implementation(project(":core:datastore"))
    implementation(libs.compose.material.icons.extended)
}
