package cn.drdream.deepseek_accountlogin

import android.app.Application
import android.content.Context
import cn.drdream.deepseek_accountlogin.api.DeepSeekApi
import cn.drdream.deepseek_accountlogin.data.AccountManager
import cn.drdream.deepseek_accountlogin.data.AttachmentStore
import cn.drdream.deepseek_accountlogin.data.ChatDatabase
import cn.drdream.deepseek_accountlogin.data.ChatRepository
import cn.drdream.deepseek_accountlogin.data.SecureStore
import cn.drdream.deepseek_accountlogin.data.SettingsStore
import cn.drdream.deepseek_accountlogin.data.history.HistoryArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DeepSeekApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Small manual dependency container; the app has one process and one account. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val secure = SecureStore(appContext)
    val database = ChatDatabase(appContext)
    val repo = ChatRepository(database)
    val attachments = AttachmentStore(appContext)
    val history = HistoryArchive(appContext, attachments)
    val api = DeepSeekApi()
    val account = AccountManager(appContext, secure, api)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
