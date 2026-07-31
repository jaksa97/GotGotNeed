package com.gotgotneed

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Got Got Need",
    ) {
        App()
    }
}