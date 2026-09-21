package dev.bmcreations.hinge.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point the iOS app target calls. The whole UI is the same `DemoApp()` Android runs.
 *
 * Posture still has to come from Swift: see `iosApp/DuoHingeSource.swift`, which registers the
 * bridge before this controller is created.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { DemoApp() }
