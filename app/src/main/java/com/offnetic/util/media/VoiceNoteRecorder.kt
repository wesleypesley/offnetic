package com.offnetic.util.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceNoteRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_DURATION_MS = com.offnetic.config.OffneticConfig.VOICE_NOTE_MAX_DURATION_MS
        const val MAX_FILE_SIZE = com.offnetic.config.OffneticConfig.VOICE_NOTE_MAX_BYTES
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L
    var isRecording: Boolean = false
        private set

    val elapsedMs: Long
        get() = if (isRecording) System.currentTimeMillis() - startTimeMs else 0L

    fun startRecording(): File {
        if (isRecording) throw IllegalStateException("Already recording")

        outputFile = File(context.filesDir, "voice_${System.currentTimeMillis()}.m4a")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(com.offnetic.config.OffneticConfig.VOICE_NOTE_SAMPLE_RATE_HZ)
            setAudioEncodingBitRate(com.offnetic.config.OffneticConfig.VOICE_NOTE_BIT_RATE)
            setOutputFile(outputFile!!.absolutePath)
            setMaxDuration(MAX_DURATION_MS)
            setMaxFileSize(MAX_FILE_SIZE)
            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                        stopRecording()
                    }
                }
            }
            try {
                prepare()
                start()
                startTimeMs = System.currentTimeMillis()
                isRecording = true
                Timber.d("Voice recording started")
            } catch (e: IOException) {
                Timber.e(e, "Failed to start recording")
                release()
                throw e
            }
        }

        return outputFile!!
    }

    /** File produced by the most recent stopRecording() — needed when the recorder
     *  auto-stops itself at the duration/size cap (chat feature #5). */
    var lastCompletedFile: File? = null
        private set

    /** Current input amplitude 0..32767, for live waveform sampling (feature #5). */
    fun maxAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping recorder")
        } finally {
            recorder = null
            isRecording = false
        }

        val file = outputFile
        outputFile = null
        lastCompletedFile = file
        Timber.d("Voice recording stopped: ${file?.length() ?: 0} bytes")
        return file
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {}

        recorder = null
        isRecording = false
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }
}