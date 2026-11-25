package cn.drdream.deepseek_accountlogin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.drdream.deepseek_accountlogin.data.AccountState
import cn.drdream.deepseek_accountlogin.data.AppLanguage
import cn.drdream.deepseek_accountlogin.ui.components.BrandMark
import cn.drdream.deepseek_accountlogin.ui.components.SoftBadge
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors

@Composable
fun LoginScreen(
    account: AccountState,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onPlatformLogin: () -> Unit,
    onManualLogin: (String) -> Unit,
    onOpenWeb: (String, String) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    var showKeyDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleLanguage) {
                    Text(if (language == AppLanguage.ZH) "English" else "中文")
                }
                SoftBadge(
                    text = strings.notOfficial,
                    color = colors.mutedText,
                    background = colors.searchCard,
                )
            }
            Spacer(modifier = Modifier.height(38.dp))
            BrandMark(size = 78.dp, withShadow = true)
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
            )
            Spacer(modifier = Modifier.height(44.dp))

            Button(
                onClick = onPlatformLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(strings.loginWithAccount)
            }
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = strings.loginWithAccountDesc,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { showKeyDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.loginWithApiKey)
            }

            Spacer(modifier = Modifier.height(34.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.searchCard,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = strings.howItWorks,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.howItWorksBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(14.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = strings.loginSecurity,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = strings.disclaimerBody,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showKeyDialog) {
        var key by remember { mutableStateOf("") }
        var visible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(strings.apiKeyTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.apiKeyLabel) },
                        placeholder = { Text(strings.apiKeyHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (visible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    imageVector = if (visible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            onOpenWeb(
                                "https://platform.deepseek.com/api_keys",
                                strings.apiKeysTitle,
                            )
                        },
                    ) {
                        Text(strings.apiKeyGet)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onManualLogin(key)
                        showKeyDialog = false
                    },
                    enabled = key.isNotBlank() && !account.signingIn,
                ) { Text(strings.apiKeySave) }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) { Text(strings.cancel) }
            },
        )
    }
}
