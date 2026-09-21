import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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

    // Produces build/XCFrameworks/<config>/Hinge.xcframework for the Swift package.
    // Rebuild with: ./gradlew :hinge-core:assembleHingeXCFramework
    val xcf = XCFramework("Hinge")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Hinge"
            // Static keeps the consuming app free of an extra dynamic framework to embed
            // and sign, which matters for a library this small.
            isStatic = true
            // Required for Flow to appear in the generated headers on the fallback path.
            export(libs.kotlinx.coroutines.core)
            xcf.add(this)
        }
    }

    /**
     * Swift export is the primary iOS integration path: it produces a real Swift module, so
     * `Flow` arrives as `AsyncSequence` and nullable primitives are not boxed.
     *
     * It is Alpha, and it only works with direct Xcode integration -- not CocoaPods, and not
     * through an SPM binaryTarget. The Xcode run-script phase becomes:
     *     ./gradlew :hinge-core:embedSwiftExportForXcode
     *
     * The XCFramework above remains the fallback for consumers who want the Objective-C
     * exporter and SPM packaging instead. Disable Swift export in gradle.properties to use it.
     */
    swiftExport {
        moduleName = "Hinge"
        flattenPackage = "dev.bmcreations.hinge"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: FoldingStateSource.state is a StateFlow and
            // foldingStateFlow() returns a Flow, both public. As `implementation` those
            // types are invisible to consumers, and the iOS export cannot see Flow at all.
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
