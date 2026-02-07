package com.zfdang.chess.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class Speaker {
    private val TAG = Speaker::class.java.simpleName

    private var tts: TextToSpeech? = null

    fun initTextToSpeech(context: Context) {
        tts = TextToSpeech(context, {
            if (it == TextToSpeech.SUCCESS) {
                val result = tts!!.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.d(TAG, "Failed to set language to CHINA")
                } else {
                    tts!!.setSpeechRate(1.2f)
                    tts!!.setPitch(0.9f)
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts!!.setAudioAttributes(attrs)
                }
            } else {
                Log.d(TAG, "Failed to initialize TTS engine")
            }
        })
    }

    fun speak(text: CharSequence) {
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    fun shutdown() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
    }
}