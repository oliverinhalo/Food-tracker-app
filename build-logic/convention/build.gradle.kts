plugins {
    `kotlin-dsl`
}

group = "dev.foodtracker.buildlogic"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "foodtracker.android.application"
            implementationClass = "dev.foodtracker.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "foodtracker.android.library"
            implementationClass = "dev.foodtracker.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "foodtracker.android.compose"
            implementationClass = "dev.foodtracker.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "foodtracker.android.hilt"
            implementationClass = "dev.foodtracker.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "foodtracker.android.feature"
            implementationClass = "dev.foodtracker.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "foodtracker.jvm.library"
            implementationClass = "dev.foodtracker.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
