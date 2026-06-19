package com.example.fiddler.subapps.Fidland.phs3.shared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioVisualizerEngine(
    private val context: Context,
    private val barCount: Int = 6,
) {
    private var visualizer: Visualizer? = null

    private val _amplitudes = MutableStateFlow(FloatArray(barCount) { 0f })
    val amplitudes: StateFlow<FloatArray> = _amplitudes

    fun start() {
        if (visualizer != null) return

        val recordGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val modifyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.MODIFY_AUDIO_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        if (!recordGranted || !modifyGranted) {
            android.util.Log.w(
                "AudioVisualizerEngine",
                "Missing permissions — RECORD_AUDIO=$recordGranted  MODIFY_AUDIO_SETTINGS=$modifyGranted"
            )
            return
        }

        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]

                // SCALING_MODE_NORMALIZED ensures the visualizer shows movement
                // even when system volume is low. SCALING_MODE_AS_PLAYED (the
                // default) stays at zero if the music volume is low.
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED

                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer, waveform: ByteArray, samplingRate: Int
                        ) { /* unused */ }

                        override fun onFftDataCapture(
                            v: Visualizer, fft: ByteArray, samplingRate: Int
                        ) {
                            // Quick check for silence
                            val hasData = fft.any { it != 0.toByte() }
                            if (!hasData) {
                                _amplitudes.value = FloatArray(barCount) { 0f }
                                return
                            }
                            _amplitudes.value = fft.toBarAmplitudes(barCount)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true,
                )
                enabled = true
            }
            android.util.Log.d(
                "AudioVisualizerEngine",
                "Visualizer started — captureSize=${visualizer?.captureSize}, mode=NORMALIZED"
            )
        } catch (e: Exception) {
            android.util.Log.e("AudioVisualizerEngine", "Visualizer init failed: ${e.message}")
        }
    }

    fun stop() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }

    companion object {
        private fun ByteArray.toBarAmplitudes(barCount: Int): FloatArray {
            // Android FFT layout (all bytes are signed):
            //   [0]        DC real      (imaginary is implicitly 0)
            //   [1]        Nyquist real (imaginary is implicitly 0)
            //   [2,3]      bin 1  re, im
            //   [4,5]      bin 2  re, im
            //   ...
            // Usable complex bins start at index 2, each consuming 2 bytes.
            val usableBins = (size / 2) - 1
            if (usableBins <= 0) return FloatArray(barCount) { 0f }

            val magnitudes = FloatArray(usableBins) { i ->
                val offset = 2 + i * 2
                if (offset + 1 >= size) return@FloatArray 0f
                val re = this[offset].toFloat()
                val im = this[offset + 1].toFloat()
                kotlin.math.sqrt(re * re + im * im)
            }

            // ── Logarithmic bucketing ─────────────────────────────────────────
            // Linear bucketing assigns equal bin counts to every bar. Because
            // music energy follows a 1/f distribution (bass always dominates),
            // bar 0 gets everything and high-frequency bars sit near zero.
            //
            // Log bucketing mirrors the mel scale — each bar covers an octave-
            // like range, so bass, mids, and treble all get one bar each and
            // every bar sees roughly equal musical energy.
            //
            // We skip bin 0 (DC component — just the average signal level,
            // not a musical frequency) and start from bin 1.
            val startBin = 1
            val endBin   = usableBins  // exclusive

            // Boundary edges for barCount+1 points spaced logarithmically
            // between startBin and endBin.
            val edges = FloatArray(barCount + 1) { i ->
                startBin * (endBin.toFloat() / startBin).pow(i.toFloat() / barCount)
            }

            // ── Peak per bar, then normalise across bars ───────────────────────
            // Using max instead of avg: a single loud transient in a sparse
            // treble bucket should show as a tall bar, not get averaged to zero.
            val raw = FloatArray(barCount) { i ->
                val from = edges[i].toInt().coerceIn(startBin, endBin - 1)
                val to   = edges[i + 1].toInt().coerceIn(from + 1, endBin)
                var peak = 0f
                for (b in from until to) peak = maxOf(peak, magnitudes[b])
                peak
            }

            // Global peak across all bars this frame — used to normalise so
            // the loudest bar always reaches 1.0. Floor at 1f avoids /0 when
            // everything is silent (the hasData guard above catches true silence,
            // but very quiet signals can still land here).
            val globalPeak = raw.max().coerceAtLeast(1f)

            return FloatArray(barCount) { i ->
                // Normalise to 0..1 relative to this frame's loudest bar,
                // then apply a sqrt curve to lift quiet bars visually — without
                // it, a bar at 10% of peak looks nearly invisible.
                kotlin.math.sqrt(raw[i] / globalPeak).coerceIn(0f, 1f)
            }
        }

        private fun Float.pow(exp: Float) = kotlin.math.exp(exp * kotlin.math.ln(this))
    }
}