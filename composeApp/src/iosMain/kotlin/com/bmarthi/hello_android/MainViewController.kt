package com.bmarthi.hello_android

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(locationProvider = IosLocationProvider()) }
