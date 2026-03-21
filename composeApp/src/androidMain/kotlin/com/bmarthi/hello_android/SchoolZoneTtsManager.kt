package com.bmarthi.hello_android

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SchoolZoneTtsManager(context: Context, private val voiceName: String? = null) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            if (voiceName != null) {
                tts.voices?.find { it.name == voiceName }?.let { tts.voice = it }
            }
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

    companion object {
        private val LOCALE_LABELS = mapOf(
            "en_US" to "US English",
            "en_GB" to "British English",
            "en_AU" to "Australian English",
            "en_IN" to "Indian English",
            "en_NG" to "Nigerian English"
        )

        fun listEnglishVoices(context: Context, callback: (List<Pair<String, String>>) -> Unit) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val voices = tts?.voices
                        ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                        ?.sortedBy { it.name }
                        ?: emptyList()

                    val counters = mutableMapOf<String, Int>()
                    val result = voices.map { voice ->
                        val localeKey = voice.locale.toString()
                        val label = LOCALE_LABELS[localeKey] ?: localeKey
                        val count = counters.getOrDefault(label, 0) + 1
                        counters[label] = count
                        voice.name to "$label $count"
                    }
                    callback(result)
                } else {
                    callback(emptyList())
                }
                tts?.shutdown()
            }
        }
    }
}
