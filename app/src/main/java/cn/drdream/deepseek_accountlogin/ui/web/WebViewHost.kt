package cn.drdream.deepseek_accountlogin.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.drdream.deepseek_accountlogin.BuildConfig
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * A deliberately small WebView host. Platform login and top-up share this process's
 * cookie/localStorage store, so signing in once covers both pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewHost(
    url: String,
    title: String,
    pollForLoginToken: Boolean,
    onToken: (String) -> Unit = {},
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val currentOpenExternal by rememberUpdatedState(onOpenExternal)
    val currentToken by rememberUpdatedState(onToken)
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var pageFinished by remember { mutableIntStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var tokenFound by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                // Login fields opt into autofill/one-time-code; on some OEM WebViews
                // the autofill connection resets the caret to offset 0 after each key.
                saveFormData = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                    progress = 0
                    errorText = null
                    if (url != null) currentUrl = url
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    progress = 100
                    if (url != null) currentUrl = url
                    pageFinished++
                    if (pollForLoginToken && view != null) {
                        // Defensive caret repair for React controlled inputs inside
                        // older OEM WebViews/IME combinations.
                        view.evaluateJavascript(CARET_FIX_JS, null)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        errorText = strings.errorNetwork
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url
                    val scheme = target.scheme?.lowercase().orEmpty()
                    return when (scheme) {
                        "http", "https", "about" -> false
                        "intent" -> {
                            runCatching {
                                val intent = Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                                context.startActivity(intent)
                            }.onFailure {
                                currentOpenExternal(target.toString())
                            }
                            true
                        }

                        else -> {
                            currentOpenExternal(target.toString())
                            true
                        }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            loadUrl(url)
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    LaunchedEffect(pageFinished, pollForLoginToken) {
        if (!pollForLoginToken || tokenFound) return@LaunchedEffect
        while (isActive && !tokenFound) {
            val raw = suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(READ_TOKEN_JS) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
            val token = decodeJsString(raw)
            if (!token.isNullOrBlank()) {
                tokenFound = true
                currentToken(token)
                break
            }
            delay(1200)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.close,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = {
                    if (currentUrl.startsWith("http")) onOpenExternal(currentUrl)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = strings.openInBrowser,
                )
            }
        }
        if (loading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            val error = errorText
            if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { errorText = null; webView.reload() }) {
                        Text(strings.retry)
                    }
                }
            }
        }
    }
}

private fun decodeJsString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
        ?: return null
    return value.takeIf { it.length > 8 }
}

private const val CARET_FIX_JS = """
(function(){
  if (window.__dsCaretFix) return;
  window.__dsCaretFix = true;
  var lastInputAt = 0;
  function isNumericField(el) {
    if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return false;
    var type = (el.getAttribute('type') || '').toLowerCase();
    var inputMode = (el.getAttribute('inputmode') || el.inputMode || '').toLowerCase();
    if (type === 'tel' || type === 'number' || inputMode === 'numeric') return true;
    var placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
    return placeholder.indexOf('phone') >= 0 ||
           placeholder.indexOf('code') >= 0 ||
           placeholder.indexOf('手机') >= 0 ||
           placeholder.indexOf('验证码') >= 0;
  }
  function restore(el) {
    try {
      if (document.activeElement !== el) return;
      var length = el.value ? el.value.length : 0;
      if (el.selectionStart !== length || el.selectionEnd !== length) {
        el.setSelectionRange(length, length);
      }
    } catch (ignore) {}
  }
  function schedule(el) {
    lastInputAt = Date.now();
    restore(el);
    [0, 30, 80, 160, 300].forEach(function(delay) {
      setTimeout(function() {
        if (Date.now() - lastInputAt <= 500) restore(el);
      }, delay);
    });
  }
  document.addEventListener('input', function(event) {
    if (isNumericField(event.target)) schedule(event.target);
  }, true);
  document.addEventListener('compositionend', function(event) {
    if (isNumericField(event.target)) schedule(event.target);
  }, true);
})()
"""

private const val READ_TOKEN_JS = """
(function(){
  try {
    var raw = localStorage.getItem('userToken') ||
              localStorage.getItem('token') ||
              sessionStorage.getItem('userToken');
    if (!raw) return '';
    var token = '';
    try {
      var parsed = JSON.parse(raw);
      if (typeof parsed === 'string') {
        token = parsed;
      } else if (parsed && typeof parsed === 'object') {
        token = parsed.token || parsed.access_token || '';
        var value = parsed.value;
        if (!token && typeof value === 'string') token = value;
        if (!token && value && typeof value === 'object') {
          token = value.token || value.access_token || value.value || '';
        }
      }
    } catch (ignore) {
      token = raw;
    }
    return (typeof token === 'string' && token.length > 8) ? token : '';
  } catch (error) {
    return '';
  }
})()
"""

fun openExternal(context: Context, url: String): Boolean {
    return runCatching {
        val intent = if (url.startsWith("intent:")) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
