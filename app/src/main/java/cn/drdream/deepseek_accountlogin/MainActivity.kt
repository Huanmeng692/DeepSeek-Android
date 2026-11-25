package cn.drdream.deepseek_accountlogin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.drdream.deepseek_accountlogin.data.AppLanguage
import cn.drdream.deepseek_accountlogin.ui.AppViewModel
import cn.drdream.deepseek_accountlogin.ui.account.PlatformLoginActivity
import cn.drdream.deepseek_accountlogin.ui.components.AccountErrorDialog
import cn.drdream.deepseek_accountlogin.ui.components.SigningInDialog
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.i18n.stringsFor
import cn.drdream.deepseek_accountlogin.ui.screens.ChatApp
import cn.drdream.deepseek_accountlogin.ui.screens.LoginScreen
import cn.drdream.deepseek_accountlogin.ui.screens.SettingsScreen
import cn.drdream.deepseek_accountlogin.ui.theme.DeepSeekTheme
import cn.drdream.deepseek_accountlogin.ui.web.WebPageActivity
import cn.drdream.deepseek_accountlogin.ui.web.openExternal

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as DeepSeekApp).container
        viewModel = ViewModelProvider(
            this,
            AppViewModel.Factory(container),
        )[AppViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val strings = stringsFor(settings.language)
            DeepSeekTheme(settings.theme) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    RootContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun RootContent(viewModel: AppViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = result.data?.getStringExtra(PlatformLoginActivity.EXTRA_TOKEN)
        val cookie = result.data?.getStringExtra(PlatformLoginActivity.EXTRA_COOKIE)
        if (result.resultCode == android.app.Activity.RESULT_OK && !token.isNullOrBlank()) {
            viewModel.loginWithPlatformToken(token, cookie)
        }
    }

    val openWeb: (String, String) -> Unit = { url, title ->
        context.startActivity(WebPageActivity.intent(context, url, title))
    }
    val launchExternal: (String) -> Unit = { url ->
        if (!openExternal(context, url)) {
            viewModel.showToast(strings.errorNetwork)
        }
    }

    LaunchedEffect(toast) {
        val message = toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearToast()
        }
    }
    LaunchedEffect(account.ready) {
        if (!account.ready) showSettings = false
        if (account.ready && !account.signingIn) viewModel.refreshAccount()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !account.ready -> LoginScreen(
                account = account,
                language = settings.language,
                onToggleLanguage = {
                    viewModel.setLanguage(
                        if (settings.language == AppLanguage.ZH) {
                            AppLanguage.EN
                        } else {
                            AppLanguage.ZH
                        },
                    )
                },
                onPlatformLogin = {
                    loginLauncher.launch(Intent(context, PlatformLoginActivity::class.java))
                },
                onManualLogin = viewModel::loginWithManualKey,
                onOpenWeb = openWeb,
            )

            showSettings -> SettingsScreen(
                viewModel = viewModel,
                account = account,
                settings = settings,
                onBack = { showSettings = false },
                onOpenWeb = openWeb,
            )

            else -> ChatApp(
                viewModel = viewModel,
                onOpenSettings = { showSettings = true },
                onOpenWeb = openWeb,
                onOpenExternal = launchExternal,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp),
        )

        if (account.signingIn) {
            SigningInDialog(step = account.loginStep)
        } else if (account.error != null) {
            AccountErrorDialog(
                error = account.error!!,
                detail = account.errorDetail,
                onDismiss = viewModel::clearAccountError,
                onOpenPlatform = {
                    viewModel.clearAccountError()
                    openWeb(
                        "https://platform.deepseek.com/api_keys",
                        strings.apiKeysTitle,
                    )
                },
                onRetry = {
                    viewModel.clearAccountError()
                    loginLauncher.launch(Intent(context, PlatformLoginActivity::class.java))
                },
            )
        }
    }
}
