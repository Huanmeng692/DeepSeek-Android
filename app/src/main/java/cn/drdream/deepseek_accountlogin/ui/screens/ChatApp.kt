package cn.drdream.deepseek_accountlogin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.ui.AppViewModel
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(
    viewModel: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val active = sessions.firstOrNull { it.id == activeId }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SessionDrawer(
                    sessions = sessions,
                    activeId = activeId,
                    account = account,
                    onNewChat = {
                        viewModel.startNewChat()
                        closeDrawer()
                    },
                    onSelect = { sessionId ->
                        viewModel.selectSession(sessionId)
                        closeDrawer()
                    },
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it },
                    onOpenSettings = {
                        closeDrawer()
                        onOpenSettings()
                    },
                    onClose = { closeDrawer() },
                )
            },
        ) {
            ChatScaffold(
                viewModel = viewModel,
                active = active,
                account = account,
                settings = settings,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = onOpenSettings,
                onOpenWeb = onOpenWeb,
                onOpenExternal = onOpenExternal,
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
            )
        }

        renameTarget?.let { target ->
            var value by remember(target.id) { mutableStateOf(target.title) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(strings.renameTitle) },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        placeholder = { Text(strings.untitled) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (value.isNotBlank()) {
                                viewModel.renameSession(target.id, value)
                            }
                            renameTarget = null
                        },
                    ) { Text(strings.save) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(strings.cancel) }
                },
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(strings.deleteChatTitle) },
                text = { Text(strings.deleteChatMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteSession(target.id)
                            deleteTarget = null
                        },
                    ) { Text(strings.delete) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text(strings.cancel) }
                },
            )
        }
    }
}
