package com.example.fiddler.core

import androidx.compose.runtime.mutableStateOf

// Shared state for sub-apps enable/disable
object SubAppState {
    val ntspdEnabled = mutableStateOf(false)
    val rngtnsEnabled = mutableStateOf(true)
    val fidlandEnabled = mutableStateOf(false)
    val secgrpEnabled = mutableStateOf(false)
}
