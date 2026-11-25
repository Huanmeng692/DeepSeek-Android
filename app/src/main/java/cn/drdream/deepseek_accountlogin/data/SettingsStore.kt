package cn.drdream.deepseek_accountlogin.data

import android.content.Context
import cn.drdream.deepseek_accountlogin.data.model.Models
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { ZH, EN }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ZH,
    val thinkingByDefault: Boolean = false,
    val searchByDefault: Boolean = false,
    val model: String = Models.FLASH,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }
    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }
    fun setThinkingByDefault(value: Boolean) = update { it.copy(thinkingByDefault = value) }
    fun setSearchByDefault(value: Boolean) = update { it.copy(searchByDefault = value) }
    fun setModel(value: String) = update { it.copy(model = Models.find(value).id) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_THEME, next.theme.name)
            .putString(KEY_LANGUAGE, next.language.name)
            .putBoolean(KEY_THINKING, next.thinkingByDefault)
            .putBoolean(KEY_SEARCH, next.searchByDefault)
            .putString(KEY_MODEL, next.model)
            .apply()
        _state.value = next
    }

    private fun load(): AppSettings {
        val language = prefs.getString(KEY_LANGUAGE, null)?.let {
            runCatching { AppLanguage.valueOf(it) }.getOrNull()
        } ?: if (Locale.getDefault().language.lowercase() == "zh") {
            AppLanguage.ZH
        } else {
            AppLanguage.EN
        }
        return AppSettings(
            theme = prefs.getString(KEY_THEME, null)?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            language = language,
            thinkingByDefault = prefs.getBoolean(KEY_THINKING, false),
            searchByDefault = prefs.getBoolean(KEY_SEARCH, false),
            model = Models.find(prefs.getString(KEY_MODEL, null) ?: Models.FLASH).id,
        )
    }

    private companion object {
        const val PREFS = "deepseek_settings"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_THINKING = "thinking"
        const val KEY_SEARCH = "search"
        const val KEY_MODEL = "model"
    }
}
