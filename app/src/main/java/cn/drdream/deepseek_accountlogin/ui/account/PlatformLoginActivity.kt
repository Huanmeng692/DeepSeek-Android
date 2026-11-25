package cn.drdream.deepseek_accountlogin.ui.account

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
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
import cn.drdream.deepseek_accountlogin.ui.web.WebViewHost
import cn.drdream.deepseek_accountlogin.ui.web.openExternal

/**
 * Signs in to the DeepSeek Platform in a WebView. On success the page's local
 * `userToken` is returned to MainActivity, which creates the app-managed API key.
 */
class PlatformLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as DeepSeekApp).container
        val activity = this
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val strings = stringsFor(settings.language)
            DeepSeekTheme(settings.theme) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    WebViewHost(
                        url = "https://platform.deepseek.com/sign_in",
                        title = strings.loginWithAccount,
                        pollForLoginToken = true,
                        onToken = { token ->
                            val cookies = runCatching {
                                CookieManager.getInstance()
                                    .getCookie("https://platform.deepseek.com")
                            }.getOrNull()
                            setResult(
                                RESULT_OK,
                                Intent()
                                    .putExtra(EXTRA_TOKEN, token)
                                    .putExtra(EXTRA_COOKIE, cookies),
                            )
                            finish()
                        },
                        onClose = { finish() },
                        onOpenExternal = { openExternal(activity, it) },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TOKEN = "deepseek_platform_token"
        const val EXTRA_COOKIE = "deepseek_platform_cookie"
    }
}
