package cn.drdream.deepseek_accountlogin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.drdream.deepseek_accountlogin.data.AccountError
import cn.drdream.deepseek_accountlogin.data.LoginStep
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors

@Composable
fun SigningInDialog(step: LoginStep) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    val steps = listOf(
        strings.stepVerifyingAccount,
        strings.stepCreatingKey,
        strings.stepVerifyingKey,
        strings.stepLoadingBalance,
    )
    val current = when (step) {
        LoginStep.VERIFYING_ACCOUNT -> 0
        LoginStep.CREATING_KEY -> 1
        LoginStep.VERIFYING_KEY -> 2
        LoginStep.LOADING_BALANCE -> 3
        LoginStep.NONE -> -1
    }
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Text(
                    text = strings.signingIn,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.size(16.dp))
                steps.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (index < current) {
                                        colors.brandSoft
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                index < current -> Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )

                                index == current -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )

                                else -> Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(colors.divider, CircleShape),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index <= current) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                colors.mutedText
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = strings.loginSecurity,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
            }
        }
    }
}

@Composable
fun AccountErrorDialog(
    error: AccountError,
    detail: String?,
    onDismiss: () -> Unit,
    onOpenPlatform: () -> Unit,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    val title = when (error) {
        AccountError.NETWORK -> strings.networkError
        AccountError.TOKEN_EXPIRED -> strings.tokenExpired
        AccountError.CREATE_KEY_FAILED -> strings.createKeyFailed
        AccountError.INVALID_KEY -> strings.apiKeyInvalid
        AccountError.BALANCE_FAILED -> strings.balanceFailed
        AccountError.UNKNOWN -> strings.unknownError
    }
    val help = when (error) {
        AccountError.CREATE_KEY_FAILED -> strings.createKeyHelp
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!detail.isNullOrBlank() && error != AccountError.TOKEN_EXPIRED) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (help != null) {
                    Text(
                        text = help,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        },
        confirmButton = {
            when (error) {
                AccountError.CREATE_KEY_FAILED -> TextButton(onClick = onOpenPlatform) {
                    Text(strings.officialPlatform)
                }

                AccountError.TOKEN_EXPIRED -> TextButton(onClick = onRetry) {
                    Text(strings.loginWithAccount)
                }

                else -> TextButton(onClick = onDismiss) { Text(strings.confirm) }
            }
        },
        dismissButton = {
            if (error == AccountError.CREATE_KEY_FAILED ||
                error == AccountError.TOKEN_EXPIRED
            ) {
                TextButton(onClick = onDismiss) { Text(strings.close) }
            }
        },
    )
}
