package com.bmarthi.hello_android

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "School District Lookup",
    ) {
        App(locationProvider = JvmLocationProvider())
    }
}
