package com.example.fiddler.subapps.rngtns

import androidx.documentfile.provider.DocumentFile

data class AudioFile(
    val docFile: DocumentFile,          // SAF file
    var keepInRotation: Boolean = true  // rotation flag
)
