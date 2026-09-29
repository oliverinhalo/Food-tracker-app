import java.io.FileInputStream
import java.util.Properties

plugins {
    id("foodtracker.android.application")
    alias(libs.plugins.kotlin.serialization)
    id("foodtracker.android.compose")
    id("foodtracker.android.hilt")
}

/**
 * Release signing, in order of preference: repository secrets passed by CI, then a local
 * `keystore.properties`, then the committed development keystore.
 *
 * That last fallback exists because Android refuses to update an installed app whose signature
 * changed. CI runners generate a fresh debug keystore per run, so every build was signed by a
 * different key and every update demanded an uninstall -- which wipes app data, including the
 * user's saved Gemini key. A stable key, even a public one, is what makes updates install over
 * the top and keep their data.
 *
 * The development keystore's password is in the repository and is therefore NOT a secret: anyone
 * can sign an APK that claims to be this app. That is an acceptable trade for a personal build
 * distributed through GitHub Releases, and it is why the real key belongs in repository secrets
 * before this is ever published anywhere that matters.
 */
// Public by design: this is the development key described above, not a secret. Declared before
// use -- a top-level val in a Kotlin build script is null until its own line has run.
val DEV_KEYSTORE_PASSWORD = "foodtracker"
val DEV_KEYSTORE_ALIAS = "foodtracker"

val keystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { FileInputStream(it).use(::load) } }

/**
 * Reads a secret, treating blank as absent.
 *
 * GitHub Actions exports an unset secret as an empty string rather than leaving the variable out,
 * and "" is not null. Without this the release build saw a keystore path of "", skipped the
 * committed development key, and fell back to a throwaway debug key -- which is exactly the
 * signature change that wipes app data on update.
 */
fun secret(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: keystoreProps?.getProperty(prop)?.takeIf { it.isNotBlank() }

val devKeystore: File = rootProject.file("signing/dev-release.jks")

val releaseKeystore: File? = (
    secret("RELEASE_KEYSTORE_PATH", "storeFile")?.let(::File)?.takeIf { it.exists() }
        ?: devKeystore.takeIf { it.exists() }
    )

val usingDevKeystore: Boolean = releaseKeystore == devKeystore

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
            if (keystore != null) {
                storeFile = keystore
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                    ?: DEV_KEYSTORE_PASSWORD.takeIf { usingDevKeystore }
                keyAlias = secret("RELEASE_KEY_ALIAS", "keyAlias")
                    ?: DEV_KEYSTORE_ALIAS.takeIf { usingDevKeystore }
                keyPassword = secret("RELEASE_KEY_PASSWORD", "keyPassword")
                    ?: DEV_KEYSTORE_PASSWORD.takeIf { usingDevKeystore }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeystore != null) {
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
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.extended)
}
