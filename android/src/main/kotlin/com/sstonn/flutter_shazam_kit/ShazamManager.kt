package com.sstonn.flutter_shazam_kit

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import com.shazam.shazamkit.*
import io.flutter.plugin.common.MethodChannel
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

// TODO: Add more comments
class ShazamManager(private val callbackChannel: MethodChannel) {
    private lateinit var catalog: Catalog
    private var recordingManager: RecordingManager = RecordingManager()
    private var currentSession: StreamingSession? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    fun configureShazamKitSession(developerToken: String?, flutterResult: MethodChannel.Result) {
        try {
            if (developerToken == null) {
                flutterResult.success(null)
                return
            }
            val tokenProvider = DeveloperTokenProvider { DeveloperToken(developerToken) }
            catalog = ShazamKit.createShazamCatalog(tokenProvider)

            coroutineScope.launch {
                when (val result =
                                ShazamKit.createStreamingSession(
                                        catalog,
                                        AudioSampleRateInHz.SAMPLE_RATE_48000,
                                        recordingManager.readBufferSize
                                )
                ) {
                    is ShazamKitResult.Success -> {
                        currentSession = result.data
                    }
                    is ShazamKitResult.Failure -> {
                        result.reason.message?.let { onError(it) }
                    }
                }
                flutterResult.success(null)
                currentSession?.let {
                    currentSession?.recognitionResults()?.collect { result: MatchResult ->
                        try {
                            when (result) {
                                is MatchResult.Match ->
                                        callbackChannel.invokeMethod(
                                                "matchFound",
                                                result.toJsonString()
                                        )
                                is MatchResult.NoMatch ->
                                        callbackChannel.invokeMethod("notFound", null)
                                is MatchResult.Error ->
                                        callbackChannel.invokeMethod(
                                                "didHasError",
                                                result.exception.message
                                        )
                            }
                        } catch (e: Exception) {
                            e.message?.let { onError(it) }
                        }
                    }
                }
    }
        } catch (e: Exception) {
            e.message?.let { onError(it) }
        }
    }

//    private suspend fun recordingFlow(): Flow<ByteArray> = flow {
//        while(isRecording) {
//            var seconds = catalog.maximumQuerySignatureDurationInMs.toInt() / 1000
//            val audioChunk = recordingManager.record(seconds)
//            emit(audioChunk)
//        }
//    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        try {
            if (currentSession == null) {
                callbackChannel.invokeMethod(
                        "didHasError",
                        "ShazamSession not found, please call configureShazamKitSession() first to initialize it."
                )
                return
            }
            callbackChannel.invokeMethod("detectStateChanged", 1)

            recordingManager.start()
            isRecording = true

            recordingThread = Thread({
                // record audio and flow it to the StreamingSession
                while(isRecording) {
//                    var seconds = catalog.maximumQuerySignatureDurationInMs.toInt() / 1000
                    val audioChunk = recordingManager.record()
                    currentSession?.matchStream(
                        audioChunk,
                        recordingManager.readBufferSize,
                        System.currentTimeMillis(),
                    )
                }
            }, "AudioRecorder Thread")
            recordingThread!!.start()


//            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
//
//            audioRecord?.startRecording()
//            isRecording = true
//            recordingThread = Thread({
//                val readBuffer = ByteArray(bufferSize)
//                while (isRecording) {
//                    val actualRead = audioRecord!!.read(readBuffer, 0, bufferSize)
//                    if (actualRead > 0) {
//                        currentSession?.matchStream(readBuffer, actualRead, System.currentTimeMillis())
//                    }
//                    else {
//                        println("Actual read is non-positive." + actualRead.toString())
//                    }
//                }
//            }, "AudioRecorder Thread")
//            recordingThread!!.start()
        } catch (e: Exception) {
            e.message?.let { onError(it) }
        }
    }

    fun stopListening() {
        callbackChannel.invokeMethod("detectStateChanged", 0)
        isRecording = false
        recordingManager.stop()
        recordingThread = null
    }

    private fun onError(message: String) {
        callbackChannel.invokeMethod("didHasError", message)
    }
}

fun MatchResult.Match.toJsonString(): String {
    val itemJsonArray = JSONArray()
    this.matchedMediaItems.forEach { item ->
        val itemJsonObject = JSONObject()
        itemJsonObject.put("title", item.title)
        itemJsonObject.put("subtitle", item.subtitle)
        itemJsonObject.put("shazamId", item.shazamID)
        itemJsonObject.put("appleMusicId", item.appleMusicID)
        item.appleMusicURL?.let { itemJsonObject.put("appleMusicUrl", it.toURI().toString()) }
        item.artworkURL?.let { itemJsonObject.put("artworkUrl", it.toURI().toString()) }
        itemJsonObject.put("artist", item.artist)
        itemJsonObject.put("matchOffset", item.matchOffsetInMs)
        item.videoURL?.let { itemJsonObject.put("videoUrl", it.toURI().toString()) }
        item.webURL?.let { itemJsonObject.put("webUrl", it.toURI().toString()) }
        itemJsonObject.put("genres", JSONArray(item.genres))
        itemJsonObject.put("isrc", item.isrc)
        itemJsonArray.put(itemJsonObject)
    }
    return itemJsonArray.toString()
}
