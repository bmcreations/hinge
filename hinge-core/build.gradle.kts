import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    // Every public declaration in this module is API. Explicit mode keeps accidental
    // additions out of the ABI and forces return types to be written down.
    explicitApi()

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        publishLibraryVariants("release")
    }

    // Targets only, no framework binaries. iOS consumers reach this module through the
    // Compose Multiplatform app's own umbrella framework, which exports it. A second
    // framework here would embed the Kotlin runtime twice and invite duplicate symbols.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: FoldingStateSource.state is a StateFlow and
            // foldingStateFlow() returns a Flow, both public. As `implementation` those
            // types would be invisible to consumers.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            api(libs.androidx.window)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "dev.bmcreations.hinge"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
