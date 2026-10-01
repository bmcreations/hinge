import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Android target as a library; the app shell is :sample:android-app.
    android {
        namespace = "dev.bmcreations.hinge.sample.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // The umbrella framework the iOS app links against. Compose Multiplatform's iOS
    // integration produces an Objective-C framework, and hinge-core is exported into it
    // below so the Swift bridge can see HingeBridgeRegistry and friends.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
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
    }
}

