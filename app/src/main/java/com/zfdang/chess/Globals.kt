package com.zfdang.chess

import com.zfdang.chess.tts.Speaker
import org.petero.droidfish.messaging.Messenger

class Globals {
    companion object {
        @JvmStatic
        val messenger = Messenger()

        @JvmStatic
        var speaker = Speaker()

        @JvmStatic
        fun speak(text: CharSequence) {
            speaker.speak(text)
        }
    }
}