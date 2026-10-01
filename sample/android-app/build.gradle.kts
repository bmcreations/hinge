plugins {
    // No kotlin-android: AGP 9 compiles Kotlin itself.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// The Android entry point only. The UI lives in :sample:compose-app, which AGP 9 no longer
// allows to apply com.android.application next to the multiplatform plugin.
android {
    namespace = "dev.bmcreations.hinge.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.bmcreations.hinge.sample"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":sample:compose-app"))
    implementation(libs.androidx.activity.compose)
}
