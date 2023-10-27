package com.sstonn.flutter_shazam_kit

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import java.nio.ByteBuffer

class RecordingManager {
    private var audioRecord: AudioRecord? = null
    private var audioFormat: AudioFormat? = null
    var readBufferSize: Int

    init {
        // Small buffer to retrieve chunks of audio
        readBufferSize = AudioRecord.getMinBufferSize(
            48_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    @WorkerThread
    fun record(): ByteArray {

        // Final desired buffer size to allocate 12 seconds of audio
//        val size = audioFormat!!.sampleRate * audioFormat!!.encoding.toByteAllocation() * seconds
//        val destination = ByteBuffer.allocate(size)



        // Make sure you are on a dedicated thread or thread pool for mic recording only and
        // elevate the priority to THREAD_PRIORITY_URGENT_AUDIO
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val readBuffer = ByteArray(readBufferSize)
        val actualRead = audioRecord!!.read(readBuffer, 0, readBufferSize)
        return readBuffer.sliceArray(0 until actualRead)
//        while (destination.remaining() > 0) {
//            val actualRead = audioRecord!!.read(readBuffer, 0, readBufferSize)
//            val byteArray = readBuffer.sliceArray(0 until actualRead)
//            destination.putTrimming(byteArray)
//        }
//        return destination.array()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun prepare() {
        val audioSource = MediaRecorder.AudioSource.UNPROCESSED
        audioFormat =
            AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48_000)
                .build()

        audioRecord =
            AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(audioFormat!!)
                .build()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        prepare()
        audioRecord?.startRecording()
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioFormat = null
    }

    private fun Int.toByteAllocation(): Int {
        return when (this) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            else -> throw IllegalArgumentException("Unsupported encoding")
        }
    }

    private fun ByteBuffer.putTrimming(byteArray: ByteArray) {
        if (byteArray.size <= this.capacity() - this.position()) {
            this.put(byteArray)
        } else {
            this.put(byteArray, 0, this.capacity() - this.position())
        }
    }
}