package cn.drdream.deepseek_accountlogin.ui.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.drdream.deepseek_accountlogin.DeepSeekApp
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.i18n.stringsFor
import cn.drdream.deepseek_accountlogin.ui.theme.DeepSeekTheme

/** In-app browser used for top-up, usage and platform settings. */
class WebPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_URL)
        if (target.isNullOrBlank() || !target.startsWith("https://")) {
            finish()
            return
        }
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as DeepSeekApp).container
        val activity = this
        val pageTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val strings = stringsFor(settings.language)
            DeepSeekTheme(settings.theme) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    WebViewHost(
                        url = target,
                        title = pageTitle.ifBlank { strings.platformTitle },
                        pollForLoginToken = false,
                        onClose = { finish() },
                        onOpenExternal = { openExternal(activity, it) },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "web_url"
        private const val EXTRA_TITLE = "web_title"

        fun intent(context: Context, url: String, title: String): Intent {
            return Intent(context, WebPageActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
        }
    }
}
