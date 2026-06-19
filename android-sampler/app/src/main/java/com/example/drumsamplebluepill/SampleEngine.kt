package com.example.drumsamplebluepill

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri

class SampleEngine(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = IntArray(8) { -1 }

    fun loadSample(context: Context, padIndex: Int, uri: Uri): Boolean {
        if (padIndex !in 0 until 8) return false
        return try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return false
            val soundId = soundPool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
            afd.close()
            if (soundIds[padIndex] != -1) soundPool.unload(soundIds[padIndex])
            soundIds[padIndex] = soundId
            true
        } catch (e: Exception) {
            false
        }
    }

    fun play(padIndex: Int, velocity: Int) {
        val soundId = soundIds[padIndex]
        if (soundId == -1) return
        val vol = velocity / 127f
        soundPool.play(soundId, vol, vol, 1, 0, 1.0f)
    }

    fun release() = soundPool.release()
}
