package cn.drdream.deepseek_accountlogin.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import cn.drdream.deepseek_accountlogin.api.AccountProfile
import cn.drdream.deepseek_accountlogin.api.ApiException
import cn.drdream.deepseek_accountlogin.api.BalanceInfo
import cn.drdream.deepseek_accountlogin.api.DeepSeekApi
import cn.drdream.deepseek_accountlogin.api.ManagedKey
import cn.drdream.deepseek_accountlogin.data.model.KeySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class LoginStep { NONE, VERIFYING_ACCOUNT, CREATING_KEY, VERIFYING_KEY, LOADING_BALANCE }

enum class AccountError {
    NETWORK,
    TOKEN_EXPIRED,
    CREATE_KEY_FAILED,
    INVALID_KEY,
    BALANCE_FAILED,
    UNKNOWN,
}

data class AccountState(
    val ready: Boolean = false,
    val keySource: KeySource = KeySource.NONE,
    val signingIn: Boolean = false,
    val loginStep: LoginStep = LoginStep.NONE,
    val profile: AccountProfile? = null,
    val wallets: List<BalanceInfo> = emptyList(),
    val keyMasked: String? = null,
    val error: AccountError? = null,
    val errorDetail: String? = null,
    val balanceLoading: Boolean = false,
    val balanceError: String? = null,
) {
    val hasBalance: Boolean get() = wallets.isNotEmpty()
    val displayBalance: BalanceInfo? get() = wallets.firstOrNull()
}

/**
 * Owns the one DeepSeek credential. Managed mode logs into Platform, creates a dedicated
 * API key there, stores both the app key and the Platform session encrypted, and revokes
 * the key on sign-out.
 */
class AccountManager(
    context: Context,
    private val secure: SecureStore,
    private val api: DeepSeekApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(loadInitial())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        api.platformCookie = secure.get(KEY_PLATFORM_COOKIE)
    }

    fun apiKey(): String? = secure.get(KEY_API)?.takeIf { it.isNotBlank() }

    fun platformToken(): String? = secure.get(KEY_PLATFORM_TOKEN)?.takeIf { it.isNotBlank() }

    fun isManaged(): Boolean = secure.get(KEY_SOURCE) == KeySource.MANAGED.name

    suspend fun loginWithManualKey(rawKey: String) {
        val key = rawKey.trim()
        _state.update {
            it.copy(
                signingIn = true,
                loginStep = LoginStep.VERIFYING_KEY,
                error = null,
                errorDetail = null,
            )
        }
        try {
            api.verifyKey(key)
            secure.put(KEY_API, key)
            secure.put(KEY_SOURCE, KeySource.MANUAL.name)
            secure.remove(KEY_PLATFORM_TOKEN)
            secure.remove(KEY_PLATFORM_COOKIE)
            secure.remove(KEY_META)
            secure.remove(KEY_PROFILE)
            secure.remove(KEY_BALANCE)
            api.platformCookie = null
            _state.value = AccountState(
                ready = true,
                keySource = KeySource.MANUAL,
                keyMasked = maskKey(key),
            )
            refreshInternal(showLoading = false)
        } catch (error: ApiException) {
            fail(error, keyCreation = false, manual = true)
        } catch (error: Exception) {
            failGeneric(error)
        }
    }

    suspend fun loginWithPlatformToken(token: String, cookie: String?) {
        api.platformCookie = cookie
        _state.update {
            it.copy(
                signingIn = true,
                loginStep = LoginStep.VERIFYING_ACCOUNT,
                error = null,
                errorDetail = null,
            )
        }
        var managed: ManagedKey? = null
        try {
            val profile = api.getPlatformProfile(token)
            _state.update { it.copy(loginStep = LoginStep.CREATING_KEY) }
            managed = api.createManagedKey(token, MANAGED_KEY_NAME)
            _state.update { it.copy(loginStep = LoginStep.VERIFYING_KEY) }
            verifyWithRetry(managed.secret)
            _state.update { it.copy(loginStep = LoginStep.LOADING_BALANCE) }
            val wallets = runCatching { api.getPlatformBalance(token) }.getOrDefault(emptyList())

            secure.put(KEY_PLATFORM_TOKEN, token)
            secure.put(KEY_PLATFORM_COOKIE, cookie.orEmpty())
            api.platformCookie = cookie
            secure.put(KEY_API, managed.secret)
            secure.put(KEY_SOURCE, KeySource.MANAGED.name)
            secure.put(KEY_META, managedKeyJson(managed).toString())
            secure.put(KEY_PROFILE, profileJson(profile).toString())
            secure.put(KEY_BALANCE, balanceJson(wallets).toString())

            _state.value = AccountState(
                ready = true,
                keySource = KeySource.MANAGED,
                profile = profile,
                wallets = wallets,
                keyMasked = maskKey(managed.secret),
            )
        } catch (error: ApiException) {
            // Do not leave an orphan key behind if verification failed after creation.
            if (managed != null) {
                runCatching { api.deleteManagedKey(token, managed) }
            }
            fail(error, keyCreation = managed != null || _state.value.loginStep == LoginStep.CREATING_KEY)
        } catch (error: Exception) {
            if (managed != null) {
                runCatching { api.deleteManagedKey(token, managed) }
            }
            failGeneric(error)
        }
    }

    fun refresh() {
        scope.launch { refreshInternal(showLoading = true) }
    }

    private suspend fun refreshInternal(showLoading: Boolean) {
        if (!showLoading && _state.value.signingIn) return
        if (showLoading) _state.update { it.copy(balanceLoading = true, balanceError = null) }
        val token = platformToken()
        try {
            api.platformCookie = secure.get(KEY_PLATFORM_COOKIE)
            if (token != null) {
                val profile = runCatching { api.getPlatformProfile(token) }.getOrNull()
                val wallets = api.getPlatformBalance(token)
                if (profile != null) secure.put(KEY_PROFILE, profileJson(profile).toString())
                secure.put(KEY_BALANCE, balanceJson(wallets).toString())
                _state.update {
                    it.copy(
                        profile = profile ?: it.profile,
                        wallets = wallets,
                        balanceLoading = false,
                        balanceError = null,
                    )
                }
            } else {
                val key = apiKey() ?: return
                val wallets = api.getApiBalance(key)
                secure.put(KEY_BALANCE, balanceJson(wallets).toString())
                _state.update {
                    it.copy(wallets = wallets, balanceLoading = false, balanceError = null)
                }
            }
        } catch (error: ApiException) {
            _state.update {
                it.copy(
                    balanceLoading = false,
                    balanceError = error.message,
                )
            }
            // A valid API key can still show the official wallet total when the
            // Platform web session has expired.
            val key = apiKey()
            if (token != null && key != null) {
                runCatching { api.getApiBalance(key) }.getOrNull()?.let { wallets ->
                    secure.put(KEY_BALANCE, balanceJson(wallets).toString())
                    _state.update { it.copy(wallets = wallets, balanceError = null) }
                }
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(balanceLoading = false, balanceError = error.message)
            }
        }
    }

    fun signOut(revokeManagedKey: Boolean) {
        scope.launch {
            val source = secure.get(KEY_SOURCE)
            val token = platformToken()
            val meta = readManagedKeyMeta()
            if (revokeManagedKey && source == KeySource.MANAGED.name && token != null && meta != null) {
                val remote = runCatching { api.listPlatformKeys(token) }.getOrNull()
                val match = remote?.firstOrNull { it.trackingId == meta.trackingId && it.trackingId != null }
                    ?: remote?.filter { it.name == MANAGED_KEY_NAME }?.singleOrNull()
                val target = if (match != null) {
                    meta.copy(
                        createdAtSeconds = match.createdAtSeconds ?: meta.createdAtSeconds,
                        trackingId = match.trackingId ?: meta.trackingId,
                        sensitiveId = match.sensitiveId ?: meta.sensitiveId,
                    )
                } else {
                    meta
                }
                runCatching { api.deleteManagedKey(token, target) }
            }
            secure.clearAll()
            api.platformCookie = null
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
            _state.value = AccountState()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null, errorDetail = null) }
    }

    /** A freshly created Platform key can take a moment to become usable. */
    private suspend fun verifyWithRetry(secret: String) {
        var last: ApiException? = null
        repeat(2) { attempt ->
            try {
                api.verifyKey(secret)
                return
            } catch (error: ApiException) {
                last = error
                if (attempt == 0) delay(900)
            }
        }
        throw last ?: ApiException(ApiException.Kind.PROTOCOL, "API Key 验证失败")
    }

    private fun fail(error: ApiException, keyCreation: Boolean, manual: Boolean = false) {
        val mapped = when {
            error.kind == ApiException.Kind.NETWORK -> AccountError.NETWORK
            error.kind == ApiException.Kind.BALANCE -> AccountError.BALANCE_FAILED
            manual && error.kind == ApiException.Kind.AUTH -> AccountError.INVALID_KEY
            keyCreation -> AccountError.CREATE_KEY_FAILED
            error.kind == ApiException.Kind.AUTH -> AccountError.TOKEN_EXPIRED
            else -> AccountError.UNKNOWN
        }
        _state.update {
            it.copy(
                signingIn = false,
                loginStep = LoginStep.NONE,
                error = mapped,
                errorDetail = error.message,
            )
        }
    }

    private fun failGeneric(error: Exception) {
        _state.update {
            it.copy(
                signingIn = false,
                loginStep = LoginStep.NONE,
                error = AccountError.UNKNOWN,
                errorDetail = error.message,
            )
        }
    }

    private fun loadInitial(): AccountState {
        val key = secure.get(KEY_API)?.takeIf { it.isNotBlank() } ?: return AccountState()
        val source = secure.get(KEY_SOURCE)?.let {
            runCatching { KeySource.valueOf(it) }.getOrNull()
        } ?: KeySource.MANUAL
        return AccountState(
            ready = true,
            keySource = source,
            keyMasked = maskKey(key),
            profile = readProfile(),
            wallets = readBalance(),
        )
    }

    private fun readProfile(): AccountProfile? {
        val raw = secure.get(KEY_PROFILE) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            AccountProfile(
                id = json.optString("id").takeIf { it.isNotBlank() },
                name = json.optString("name").takeIf { it.isNotBlank() },
                email = json.optString("email").takeIf { it.isNotBlank() },
                mobile = json.optString("mobile").takeIf { it.isNotBlank() },
                avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun readBalance(): List<BalanceInfo> {
        val raw = secure.get(KEY_BALANCE) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let {
                    BalanceInfo(
                        currency = it.optString("currency"),
                        total = it.optString("total"),
                        granted = it.optString("granted"),
                        toppedUp = it.optString("toppedUp"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readManagedKeyMeta(): ManagedKey? {
        val raw = secure.get(KEY_META) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ManagedKey(
                secret = secure.get(KEY_API).orEmpty(),
                createdAtSeconds = json.optLong("createdAt").takeIf { it > 0 },
                trackingId = json.optString("trackingId").takeIf { it.isNotBlank() },
                sensitiveId = json.optString("sensitiveId").takeIf { it.isNotBlank() }
                    ?: secure.get(KEY_API),
            )
        }.getOrNull()
    }

    private fun profileJson(profile: AccountProfile): JSONObject = JSONObject().apply {
        profile.id?.let { put("id", it) }
        profile.name?.let { put("name", it) }
        profile.email?.let { put("email", it) }
        profile.mobile?.let { put("mobile", it) }
        profile.avatarUrl?.let { put("avatarUrl", it) }
    }

    private fun balanceJson(wallets: List<BalanceInfo>): JSONArray = JSONArray().apply {
        wallets.forEach { wallet ->
            put(
                JSONObject().apply {
                    put("currency", wallet.currency)
                    put("total", wallet.total)
                    put("granted", wallet.granted)
                    put("toppedUp", wallet.toppedUp)
                },
            )
        }
    }

    private fun managedKeyJson(key: ManagedKey): JSONObject = JSONObject().apply {
        key.createdAtSeconds?.let { put("createdAt", it) }
        key.trackingId?.let { put("trackingId", it) }
        key.sensitiveId?.let { put("sensitiveId", it) }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 10) return "sk-••••"
        return key.take(6) + "••••••" + key.takeLast(4)
    }

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_PLATFORM_TOKEN = "platform_token"
        private const val KEY_PLATFORM_COOKIE = "platform_cookie"
        private const val KEY_SOURCE = "key_source"
        private const val KEY_META = "managed_key_meta"
        private const val KEY_PROFILE = "account_profile"
        private const val KEY_BALANCE = "account_balance"
        private const val MANAGED_KEY_NAME = "DeepSeek Android"
    }
}
