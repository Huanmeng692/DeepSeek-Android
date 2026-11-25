package cn.drdream.deepseek_accountlogin.ui.components

import cn.drdream.deepseek_accountlogin.api.BalanceInfo
import cn.drdream.deepseek_accountlogin.ui.i18n.Strings
import java.math.BigDecimal

fun walletSymbol(currency: String): String = when (currency.uppercase()) {
    "CNY" -> "¥"
    "USD" -> "$"
    else -> currency.uppercase() + " "
}

fun formatAmount(value: String): String {
    return runCatching {
        val number = BigDecimal(value).stripTrailingZeros()
        if (number.compareTo(BigDecimal.ZERO) == 0) "0" else number.toPlainString()
    }.getOrDefault(value)
}

fun formatWallet(wallet: BalanceInfo): String =
    walletSymbol(wallet.currency) + formatAmount(wallet.total)

fun formatBalance(wallets: List<BalanceInfo>): String {
    if (wallets.isEmpty()) return "—"
    return wallets.joinToString("  ") { formatWallet(it) }
}

fun bonusText(wallets: List<BalanceInfo>, strings: Strings): String? {
    val withBonus = wallets.filter { wallet ->
        runCatching { BigDecimal(wallet.granted).compareTo(BigDecimal.ZERO) > 0 }
            .getOrDefault(false)
    }
    if (withBonus.isEmpty()) return null
    return withBonus.joinToString("  ") { wallet ->
        "${walletSymbol(wallet.currency)}${formatAmount(wallet.granted)}"
    }
}
