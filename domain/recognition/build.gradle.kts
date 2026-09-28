plugins {
    id("foodtracker.jvm.library")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:text"))
}
