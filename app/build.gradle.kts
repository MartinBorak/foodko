plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// `./gradlew installDebug -PfoodkoMock=true` builds a debug app that talks to the local Firebase
// emulators with fake accounts and demo data. Release builds never get this.
val foodkoMock = providers.gradleProperty("foodkoMock").map { it.toBoolean() }.getOrElse(false)

// Version from git so every release pushed through App Distribution is newer than the last:
// versionCode = number of commits, versionName = 0.1.<commits> (+ short sha).
val gitCommitCount = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)
val gitShortSha = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }.getOrElse("local")

android {
    namespace = "app.foodko"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "app.foodko"
        minSdk = 33
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "0.1.$gitCommitCount ($gitShortSha)"
        buildConfigField("boolean", "MOCK", "false")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "MOCK", foodkoMock.toString())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Both phones are installed from this machine over USB, so release builds share
            // the debug signature. Keeps one SHA-1 registered in Firebase for both build types.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
