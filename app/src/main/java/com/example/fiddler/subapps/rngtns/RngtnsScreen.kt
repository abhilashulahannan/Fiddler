package com.example.fiddler.subapps.rngtns

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.fiddler.R


@Composable
fun RngtnsScreen() {
    val context = LocalContext.current
    val audioFiles = remember { mutableStateListOf<AudioFile>() }

    // Ideally you get prefs from context; here we simulate
    val prefs = context.getSharedPreferences("fiddler_prefs", Context.MODE_PRIVATE)

    val enableRotation = remember { mutableStateOf(false) }
    val rotationStyle = remember { mutableStateOf("Each day") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(60.dp))
        Text("Audio", fontSize = 54.sp, fontFamily = FontFamily(Font(R.font.font_body)))
        Text("Configurable elements like rotating ringtones, etc.",
            fontSize = 20.sp, fontFamily = FontFamily(Font(R.font.font_handwriting)))

        Spacer(modifier = Modifier.height(32.dp))
        Text("Rotating Ringtones", fontSize = 28.sp, fontFamily = FontFamily(Font(R.font.font_body)))
        Text("Set multiple audio files as ringtones which switches after every ring.",
            fontSize = 20.sp, fontFamily = FontFamily(Font(R.font.font_handwriting)))

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Checkbox(checked = enableRotation.value, onCheckedChange = { enableRotation.value = it })
            Text("Enable Ringtone Rotation", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Rotation Style:", fontSize = 22.sp)
        Column {
            listOf("Each ring", "Each hour", "Each day").forEach { style ->
                Row {
                    RadioButton(selected = rotationStyle.value == style, onClick = { rotationStyle.value = style })
                    Text(style, fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = { syncAudio(context, audioFiles, prefs) }) { Text("Sync Directory") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { triggerRotation(context, audioFiles) }) { Text("Trigger Rotate") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Songs in rotation:", fontSize = 22.sp)
        LazyColumn(modifier = Modifier.height(250.dp)) {
            items(audioFiles) { audio ->
                AudioItem(audio)
            }
        }
    }
}

@Composable
fun AudioItem(audio: AudioFile) {
    val checkedState = remember { mutableStateOf(audio.keepInRotation) }
    Row(modifier = Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checkedState.value, onCheckedChange = {
            checkedState.value = it
            audio.keepInRotation = it
        })
        Text(
            text = audio.docFile.name ?: "",
            modifier = Modifier.padding(start = 8.dp),
            fontFamily = if (checkedState.value) FontFamily(Font(R.font.font_handwriting)) else FontFamily(Font(R.font.font_body)),
            fontSize = 20.sp
        )
    }
}

// Functions moved outside composable
fun syncAudio(context: Context, audioFiles: MutableList<AudioFile>, prefs: android.content.SharedPreferences) {
    audioFiles.clear()
    val uriString = prefs.getString("saf_uri", null) ?: return
    val treeUri = Uri.parse(uriString)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
    val audioFolder = rootDoc.findFile("Audio") ?: rootDoc
    val fidtonesFolder = audioFolder.findFile("Fidtones") ?: audioFolder

    fidtonesFolder.listFiles()
        .filter { it.isFile && it.name?.endsWith(".ogg", true) == true }
        .sortedBy { it.name?.lowercase() }
        .forEach { audioFiles.add(AudioFile(it, keepInRotation = true)) }
}

fun triggerRotation(context: Context, audioFiles: List<AudioFile>) {
    val activeFiles = audioFiles.filter { it.keepInRotation }
    if (activeFiles.isEmpty()) {
        Toast.makeText(context, "No active audio files for rotation", Toast.LENGTH_SHORT).show()
        return
    }

    val nextFile = activeFiles.random()
    try {
        val inputStream = context.contentResolver.openInputStream(nextFile.docFile.uri) ?: run {
            Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "fiddler_ringtone.ogg")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
        }

        val resolver = context.contentResolver
        val ringtoneUri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: run {
                Toast.makeText(context, "Failed to create ringtone file", Toast.LENGTH_SHORT).show()
                return
            }

        resolver.openOutputStream(ringtoneUri)?.use { output ->
            inputStream.copyTo(output)
        }

        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, ringtoneUri)
        Toast.makeText(context, "${nextFile.docFile.name} set as system ringtone", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to rotate ringtone", Toast.LENGTH_SHORT).show()
    }
}
