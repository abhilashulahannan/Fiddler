package com.example.fiddler.subapps.rngtns

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.fiddler.R
import com.example.fiddler.core.SubAppState

@Composable
fun RngtnsScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("fiddler_prefs", Context.MODE_PRIVATE)
    }

    val audioFiles = remember { mutableStateListOf<AudioFile>() }

    // Restore persisted rotation style
    var rotationStyle by remember {
        mutableStateOf(prefs.getString("rotation_style", "Each day") ?: "Each day")
    }

    val fontBody = FontFamily(Font(R.font.font_body))
    val fontHandwriting = FontFamily(Font(R.font.font_handwriting))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "Audio",
            fontFamily = fontBody,
            fontSize = 54.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Configurable elements like rotating ringtones, etc.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Rotating Ringtones",
            fontFamily = fontBody,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Set multiple audio files as ringtones which switch after every ring.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = SubAppState.rngtnsEnabled.value,
                onCheckedChange = { SubAppState.rngtnsEnabled.value = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "Enable Ringtone Rotation",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Rotation Style:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 5.dp)
        )

        Column {
            listOf("Each ring", "Each hour", "Each day").forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = rotationStyle == style,
                        onClick = {
                            rotationStyle = style
                            prefs.edit().putString("rotation_style", style).apply()
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.Black,
                            unselectedColor = Color.Black,
                            disabledSelectedColor = Color.Gray,
                            disabledUnselectedColor = Color.Gray
                        )
                    )
                    Text(
                        text = style,
                        fontFamily = fontHandwriting,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Songs in rotation:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { syncAudio(context, audioFiles, prefs) },
                shape = MaterialTheme.shapes.medium,
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    "Sync Directory",
                    fontFamily = fontHandwriting,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            OutlinedButton(
                onClick = { triggerRotation(context, audioFiles) },
                shape = MaterialTheme.shapes.medium,
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    "Trigger Rotate",
                    fontFamily = fontHandwriting,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (audioFiles.isEmpty()) {
            Text(
                text = "No audio files found. Tap 'Sync Directory' to load from your Fidtones folder.",
                fontFamily = fontHandwriting,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(color = Color.White, shape = RoundedCornerShape(20.dp))
                    .border(
                        width = 2.dp,
                        color = Color.Gray.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(audioFiles) { audio ->
                        AudioItem(audio)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun AudioItem(audio: AudioFile) {
    var checked by remember { mutableStateOf(audio.keepInRotation) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                audio.keepInRotation = it
            }
        )
        Text(
            text = audio.docFile.name ?: "",
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// -------------------- Helper functions --------------------

fun syncAudio(
    context: Context,
    audioFiles: MutableList<AudioFile>,
    prefs: android.content.SharedPreferences
) {
    audioFiles.clear()
    val uriString = prefs.getString("saf_uri", null) ?: run {
        Toast.makeText(context, "No folder selected. Please set up in Permissions.", Toast.LENGTH_LONG).show()
        return
    }
    val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return
    val fidtonesFolder = rootDoc.findFile("Audio")?.findFile("Fidtones") ?: run {
        Toast.makeText(context, "Fidtones folder not found.", Toast.LENGTH_SHORT).show()
        return
    }

    fidtonesFolder.listFiles()
        .filter { it.isFile && it.name?.endsWith(".ogg", ignoreCase = true) == true }
        .sortedBy { it.name?.lowercase() }
        .forEach { audioFiles.add(AudioFile(it, keepInRotation = true)) }

    Toast.makeText(context, "${audioFiles.size} file(s) loaded", Toast.LENGTH_SHORT).show()
}

fun triggerRotation(context: Context, audioFiles: List<AudioFile>) {
    val activeFiles = audioFiles.filter { it.keepInRotation }
    if (activeFiles.isEmpty()) {
        Toast.makeText(context, "No active audio files for rotation.", Toast.LENGTH_SHORT).show()
        return
    }

    val nextFile = activeFiles.random()
    val resolver = context.contentResolver

    try {
        val inputStream = resolver.openInputStream(nextFile.docFile.uri) ?: run {
            Toast.makeText(context, "Failed to read file.", Toast.LENGTH_SHORT).show()
            return
        }

        // Try to find an existing fiddler_ringtone.ogg entry in MediaStore
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val existingUri: Uri? = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("fiddler_ringtone.ogg"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                Uri.withAppendedPath(collection, id.toString())
            } else null
        }

        val ringtoneUri: Uri = if (existingUri != null) {
            // Update existing entry's audio data in place
            resolver.openOutputStream(existingUri, "wt")?.use { output ->
                inputStream.copyTo(output)
            }
            existingUri
        } else {
            // First time — insert a new entry
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "fiddler_ringtone.ogg")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }
            val newUri = resolver.insert(collection, values) ?: run {
                Toast.makeText(context, "Failed to create ringtone entry.", Toast.LENGTH_SHORT).show()
                return
            }
            resolver.openOutputStream(newUri)?.use { output ->
                inputStream.copyTo(output)
            }
            newUri
        }

        RingtoneManager.setActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
            ringtoneUri
        )

        Toast.makeText(context, "${nextFile.docFile.name} set as ringtone", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to rotate ringtone.", Toast.LENGTH_SHORT).show()
    }
}