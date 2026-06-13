package com.example.fiddler.core

import androidx.compose.runtime.mutableStateOf

// Shared state for sub-apps enable/disable
object SubAppState {
    val ntspdEnabled = mutableStateOf(true)
    val rngtnsEnabled = mutableStateOf(true)
    val fidlandEnabled = mutableStateOf(false)
    val secgrpEnabled = mutableStateOf(false)
}
