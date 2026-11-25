package cn.drdream.deepseek_accountlogin.ui.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Thin TextToSpeech wrapper; markdown is flattened before speaking. */
class TtsController(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private var pending: Pair<String, String>? = null
    var onFinished: (() -> Unit)? = null
    var isSpeaking: Boolean = false
        private set

    private val tts: TextToSpeech
    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            failed = status != TextToSpeech.SUCCESS
            if (ready) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post {
                        isSpeaking = true
                    }
                }

                override fun onDone(utteranceId: String?) {
                    main.post {
                        isSpeaking = false
                        onFinished?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post {
                        isSpeaking = false
                        onFinished?.invoke()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post {
                        isSpeaking = false
                        onFinished?.invoke()
                    }
                }
            })
            pending?.let { (text, language) ->
                pending = null
                speakNow(text, language)
            }
        } else {
            pending = null
        }
    }
    }

    fun speak(text: String, languageTag: String): Boolean {
        val clean = flattenMarkdown(text)
        if (clean.isBlank()) return false
        if (failed) return false
        if (!ready) {
            pending = clean to languageTag
            return true
        }
        speakNow(clean, languageTag)
        return true
    }

    fun stop() {
        runCatching { tts.stop() }
        isSpeaking = false
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        onFinished = null
    }

    private fun speakNow(text: String, languageTag: String) {
        val locale = Locale.forLanguageTag(languageTag)
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(Locale.getDefault())
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deepseek-${System.nanoTime()}")
        isSpeaking = true
    }

    private fun flattenMarkdown(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " 代码已省略 ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("[#>*_~|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
