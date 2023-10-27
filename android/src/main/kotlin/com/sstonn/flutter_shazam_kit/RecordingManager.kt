package com.sstonn.flutter_shazam_kit

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread


class RecordingManager {
    private var audioRecord: AudioRecord? = null
    private var audioFormat: AudioFormat? = null
    var readBufferSize: Int
    var threshold: Short = 100


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
        // Make sure you are on a dedicated thread or thread pool for mic recording only and
        // elevate the priority to THREAD_PRIORITY_URGENT_AUDIO
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        return try {
            val readBuffer = ByteArray(readBufferSize)
            val actualRead = audioRecord!!.read(readBuffer, 0, readBufferSize)
            if( actualRead <= 0 ) throw Exception("Error reading audio buffer")
            readBuffer.sliceArray(0 until actualRead)
            if (readBuffer.check(actualRead)) {
                // println("Max value is ${readBuffer.maxOrNull()}")
                readBuffer
            }
            else
                ByteArray(0)
        } catch (e: Exception) {
            println(e)
            ByteArray(0)
        }
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

    fun searchThreshold(arr: ByteArray, thr: Short): Int {
        var peakIndex: Int
        val arrLen = arr.size
        peakIndex = 0
        while (peakIndex < arrLen) {
            if (arr[peakIndex] >= thr || arr[peakIndex] <= -thr) {
                //se supera la soglia, esci e ritorna peakindex-mezzo kernel.
                return peakIndex
            }
            peakIndex++
        }
        return -1 //not found
    }

    private fun ByteArray.check(actualRead: Int) :Boolean{
        if (AudioRecord.ERROR_INVALID_OPERATION != actualRead) {
            //check signal
            //put a threshold
            val foundPeak = searchThreshold(this, threshold)
            return foundPeak > -1
        }
        return false
    }
}