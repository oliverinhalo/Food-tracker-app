import java.io.FileInputStream
import java.util.Properties

plugins {
    id("foodtracker.android.application")
    alias(libs.plugins.kotlin.serialization)
    id("foodtracker.android.compose")
    id("foodtracker.android.hilt")
}

/**
 * Release signing comes from (in order): environment variables set by CI from repository secrets,
 * then a local `keystore.properties`, then nothing -- in which case the release build falls back to
 * the debug signing config so it is still installable. A fallback-signed APK cannot upgrade a
 * properly signed install, which the release workflow calls out in its notes.
 */
val releaseKeystore: File? = System.getenv("RELEASE_KEYSTORE_PATH")?.let(::File)
    ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let { propsFile ->
        val props = Properties().apply { FileInputStream(propsFile).use(::load) }
        props.getProperty("storeFile")?.let(rootProject::file)
    }

val keystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { FileInputStream(it).use(::load) } }

fun secret(env: String, prop: String): String? =
    System.getenv(env) ?: keystoreProps?.getProperty(prop)

android {
    namespace = "dev.foodtracker"

    defaultConfig {
        applicationId = "dev.foodtracker"
        // CI overrides both from the release tag; the defaults are what a local build gets.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystore = releaseKeystore
            if (keystore != null && keystore.exists()) {
                storeFile = keystore
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = secret("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = secret("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeystore?.exists() == true) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":data:recognition"))
    implementation(project(":data:nutrition"))
    implementation(project(":data:diary"))
    implementation(project(":core:database"))
    implementation(project(":domain:nutrition"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:results"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.extended)
}
