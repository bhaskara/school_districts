package com.bmarthi.hello_android

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SchoolZoneTtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            ready = true
        }
    }

    fun announceSchools(schools: List<School>) {
        if (!ready || schools.isEmpty()) return
        val names = schools.joinToString(", ") { it.name }
        tts.speak("Entering school zone: $names", TextToSpeech.QUEUE_FLUSH, null, "school_zone_alert")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
