package com.example.fiddler.subapps.rngtns

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R
import android.provider.MediaStore

class RngtnsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var switchEnable: CheckBox
    private lateinit var radioGroup: RadioGroup
    private lateinit var rotateButton: Button
    private lateinit var syncButton: Button
    private lateinit var audioAdapter: AudioAdapter
    private val audioFiles = mutableListOf<AudioFile>()

    private val prefs by lazy { requireContext().getSharedPreferences("fiddler_prefs", Context.MODE_PRIVATE) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_rngtns, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.audioRecyclerView)
        switchEnable = view.findViewById(R.id.switch_enable)
        radioGroup = view.findViewById(R.id.radio_group_placement)
        rotateButton = view.findViewById(R.id.btn_rotate_now)
        syncButton = view.findViewById(R.id.btn_sync)

        audioAdapter = AudioAdapter(audioFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = audioAdapter

        ensureFoldersExist()
        loadAudioFiles()

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            // TODO: implement automatic rotation toggle if needed
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_er -> { /* Each ring */ }
                R.id.radio_eh -> { /* Each hour */ }
                R.id.radio_ed -> { /* Each day */ }
            }
        }

        rotateButton.setOnClickListener { triggerRotation() }

        syncButton.setOnClickListener {
            loadAudioFiles()
            Toast.makeText(requireContext(), "Audio folder synced", Toast.LENGTH_SHORT).show()
        }
    }

    /** Ensure Audio/Fidtones folders exist in SAF */
    private fun ensureFoldersExist() {
        val uriString = prefs.getString("saf_uri", null) ?: return
        val treeUri = Uri.parse(uriString)
        val rootDoc = DocumentFile.fromTreeUri(requireContext(), treeUri) ?: return

        val audioFolder = rootDoc.findFile("Audio") ?: rootDoc.createDirectory("Audio")
        audioFolder?.findFile("Fidtones") ?: audioFolder?.createDirectory("Fidtones")
    }

    /** Load .ogg files from Fidtones folder using SAF */
    private fun loadAudioFiles() {
        audioFiles.clear()
        val uriString = prefs.getString("saf_uri", null) ?: return
        val treeUri = Uri.parse(uriString)
        val rootDoc = DocumentFile.fromTreeUri(requireContext(), treeUri) ?: return

        val audioFolder = rootDoc.findFile("Audio") ?: rootDoc
        val fidtonesFolder = audioFolder.findFile("Fidtones") ?: audioFolder

        fidtonesFolder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".ogg", true) == true }
            .sortedBy { it.name?.lowercase() }
            .forEach { audioFiles.add(AudioFile(it, keepInRotation = true)) }

        audioAdapter.notifyDataSetChanged()
    }

    /** Rotate ringtone: copy random active file to MediaStore Ringtones and set it */
    private fun triggerRotation() {
        val activeFiles = audioFiles.filter { it.keepInRotation }
        if (activeFiles.isEmpty()) {
            Toast.makeText(requireContext(), "No active audio files for rotation", Toast.LENGTH_SHORT).show()
            return
        }

        val nextFile = activeFiles.random()

        try {
            val inputStream = requireContext().contentResolver.openInputStream(nextFile.docFile.uri)
                ?: run {
                    Toast.makeText(requireContext(), "Failed to read file", Toast.LENGTH_SHORT).show()
                    return
                }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "fiddler_ringtone.ogg")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }

            val resolver = requireContext().contentResolver
            val ringtoneUri = resolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: run {
                Toast.makeText(requireContext(), "Failed to create ringtone file", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(ringtoneUri)?.use { output ->
                inputStream.copyTo(output)
            }

            // Set as system default ringtone
            RingtoneManager.setActualDefaultRingtoneUri(
                requireContext(),
                RingtoneManager.TYPE_RINGTONE,
                ringtoneUri
            )

            Toast.makeText(requireContext(), "${nextFile.docFile.name} set as system ringtone", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to rotate ringtone", Toast.LENGTH_SHORT).show()
        }
    }
}
