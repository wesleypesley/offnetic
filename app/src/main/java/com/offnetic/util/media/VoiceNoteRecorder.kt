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
        const val MAX_DURATION_MS = 2 * 60 * 1000
        const val MAX_FILE_SIZE = (1.5 * 1024 * 1024).toLong()
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
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(32000)
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