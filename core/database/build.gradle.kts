plugins {
    id("foodtracker.android.library")
    id("foodtracker.android.hilt")
}

android {
    namespace = "dev.foodtracker.core.database"

    // Room's generated schema JSON is committed, so migrations can be written and tested against
    // the real previous schema rather than a remembered one.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.room.testing)
}
