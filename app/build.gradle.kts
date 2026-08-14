import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The google-services plugin generates Firebase config resources from
// app/google-services.json, which is per-developer Firebase project config and
// deliberately NOT checked in. Apply it only when the file is present so the
// project keeps building (and CI stays green) without it; the app then reports
// a friendly "sign-in not configured" state instead of crashing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// The API keys above are read from local.properties at configuration time,
// but Gradle does NOT track that file — so editing a key would silently leave
// the previous build's BuildConfig in place (stale keys in old dex files).
// Declaring it as an input makes every build-config task re-run on key edits.
tasks.configureEach {
    if (name == "generateDebugBuildConfig" || name == "generateReleaseBuildConfig") {
        inputs.file(rootProject.file("local.properties"))
    }
}

// --- Release signing ---------------------------------------------------------
// The release APK is signed when a keystore is available: from a local
// keystore.properties (gitignored, for personal builds) or from environment
// variables (set by the CI workflow, which decodes the keystore secret). With
// neither, the release build stays unsigned so local/CI debug builds never
// break.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProperty(name: String, env: String): String? =
    keystoreProperties.getProperty(name) ?: System.getenv(env)

val keystorePathRaw = System.getenv("KEYSTORE_FILE")
    ?: keystoreProperties.getProperty("storeFile")
val keystoreFile = keystorePathRaw?.let { raw ->
    val f = File(raw)
    if (f.isAbsolute) f else rootProject.file(raw)
}
// Note: these names deliberately differ from SigningConfig's members
// (storePassword/keyAlias/keyPassword) — inside `create("release")` the
// receiver's members would shadow the locals and the assignment would set
// the property to itself.
val ksStorePassword = signingProperty("storePassword", "KEYSTORE_PASSWORD")
val ksKeyAlias = signingProperty("keyAlias", "KEY_ALIAS")
val ksKeyPassword = signingProperty("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning = keystoreFile != null && keystoreFile.exists() &&
    ksStorePassword != null && ksKeyAlias != null && ksKeyPassword != null

// AI keys: Blurt ships zero build-time keys. Users bring their own free keys
// in-app (avatar → AI keys → BYOK), stored encrypted in the Android Keystore —
// so the APK never contains a secret and the project always builds keyless.
android {
    namespace = "com.blurt.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blurt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile!!
                storePassword = ksStorePassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Needed by Robolectric for Android framework behavior (Uri, SQLite, resources).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // Authentication — Google Sign-In via Firebase Auth. The Firebase BoM
    // pins all Firebase artifacts to compatible versions.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
}
