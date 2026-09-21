import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // A plain Objective-C framework, not Swift export. The iOS app's only Swift code is one
    // line calling MainViewController(), so there is nothing here that benefits from Swift
    // export -- and the standard Compose Multiplatform integration expects this shape.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Without this the Obj-C headers contain no HingeBridgeRegistry, HingeBridge or
            // HingeSnapshot, and iosApp/DuoHingeSource.swift cannot see a single symbol.
            // export() is not implied by a transitive dependency and is not transitive
            // itself, so hinge-core is named directly.
            export(project(":hinge-core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, because export() above only accepts api dependencies.
            api(project(":hinge-compose"))
            api(project(":hinge-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "dev.bmcreations.hinge.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    // The KMP Android source-set layout remaps Kotlin dirs and AndroidManifest.xml, but not
    // res/. Without this, @style/Theme.Hinge does not resolve.
    sourceSets["main"].res.srcDirs("src/androidMain/res")

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
}
