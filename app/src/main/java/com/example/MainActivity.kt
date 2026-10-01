package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.telephony.TelephonyRoleHelper
import com.example.ui.blocked.BlockedNumbersScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.conversationlist.ConversationListScreen
import com.example.ui.conversationlist.ConversationListViewModel
import com.example.ui.newmessage.NewMessageScreen
import com.example.ui.newmessage.NewMessageViewModel
import com.example.ui.scheduled.ScheduledMessagesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.SalimTheme

sealed class Screen {
    object ConversationList : Screen()
    data class Chat(val conversationId: Long, val recipientAddress: String) : Screen()
    object NewMessage : Screen()
    object ScheduledMessages : Screen()
    object BlockedNumbers : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val conversationListViewModel: ConversationListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SalimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalimApp(
                        conversationListViewModel = conversationListViewModel,
                        initialIntent = intent
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun SalimApp(
    conversationListViewModel: ConversationListViewModel,
    initialIntent: Intent?
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ConversationList) }

    val convListState by conversationListViewModel.uiState.collectAsStateWithLifecycle()

    // Permission Launchers
    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        conversationListViewModel.syncRealMessages()
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        conversationListViewModel.syncRealMessages()
    }

    // Default SMS Role Launcher
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        conversationListViewModel.syncRealMessages()
    }

    val requestAllSmsPermissions = {
        val permissions = mutableListOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermissionsLauncher.launch(permissions.toTypedArray())
    }

    val requestContactsPermission = {
        contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
    }

    val requestDefaultSmsApp = {
        val context = SalimApplication.instance
        val intent = TelephonyRoleHelper.createDefaultSmsIntent(context)
        if (intent != null) {
            defaultSmsLauncher.launch(intent)
        }
    }

    // Automatically request permissions once on startup and sync real messages
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermissionsLauncher.launch(permissions.toTypedArray())
        conversationListViewModel.syncRealMessages()
    }

    // Handle initial intent (e.g. from notification or sendto: external intent)
    LaunchedEffect(initialIntent) {
        if (initialIntent != null) {
            val convId = initialIntent.getLongExtra("EXTRA_CONVERSATION_ID", -1L)
            val recipient = initialIntent.getStringExtra("EXTRA_RECIPIENT_ADDRESS")
            if (convId > 0 && !recipient.isNullOrBlank()) {
                currentScreen = Screen.Chat(convId, recipient)
            } else if (initialIntent.data != null) {
                val scheme = initialIntent.data?.scheme
                if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                    val ssp = initialIntent.data?.schemeSpecificPart ?: ""
                    val cleanNumber = ssp.substringBefore('?')
                    if (cleanNumber.isNotBlank()) {
                        currentScreen = Screen.Chat(-1L, cleanNumber)
                    }
                }
            }
        }
    }

    when (val screen = currentScreen) {
        is Screen.ConversationList -> {
            ConversationListScreen(
                state = convListState,
                onTabSelected = { conversationListViewModel.setTab(it) },
                onSearchQueryChanged = { conversationListViewModel.setSearchQuery(it) },
                onSetIsSearching = { conversationListViewModel.setIsSearching(it) },
                onConversationClicked = { id, address ->
                    currentScreen = Screen.Chat(id, address)
                },
                onTogglePin = { conversationListViewModel.togglePin(it) },
                onToggleArchive = { conversationListViewModel.toggleArchive(it) },
                onToggleSpam = { conversationListViewModel.toggleSpam(it) },
                onDeleteConversation = { conversationListViewModel.deleteConversation(it) },
                onComposeClicked = { currentScreen = Screen.NewMessage },
                onOpenScheduled = { currentScreen = Screen.ScheduledMessages },
                onOpenBlocked = { currentScreen = Screen.BlockedNumbers },
                onOpenSettings = { currentScreen = Screen.Settings },
                onSimulateIncomingSms = { sender, msg ->
                    conversationListViewModel.simulateIncomingSms(sender, msg)
                },
                onSeedDemoChats = {
                    conversationListViewModel.seedDemoConversations()
                },
                onRefreshRealMessages = {
                    conversationListViewModel.syncRealMessages()
                }
            )
        }

        is Screen.Chat -> {
            val chatViewModel: ChatViewModel = viewModel()
            LaunchedEffect(screen.conversationId, screen.recipientAddress) {
                chatViewModel.initialize(screen.conversationId, screen.recipientAddress)
            }
            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

            ChatScreen(
                state = chatState,
                onBackClicked = { currentScreen = Screen.ConversationList },
                onInputTextChanged = { chatViewModel.onInputTextChanged(it) },
                onSelectQuickReply = { chatViewModel.onSelectQuickReply(it) },
                onSetAttachmentUri = { chatViewModel.setAttachmentUri(it) },
                onSetScheduledTime = { chatViewModel.setScheduledTime(it) },
                onSetPreviewMediaUri = { chatViewModel.setPreviewMediaUri(it) },
                onSendMessage = { chatViewModel.sendMessage() },
                onRetryMessage = { chatViewModel.retryMessage(it) },
                onDeleteMessage = { chatViewModel.deleteMessage(it) },
                onDeleteConversation = {
                    chatViewModel.deleteConversation()
                    currentScreen = Screen.ConversationList
                },
                onToggleBlockContact = {
                    chatViewModel.toggleBlockContact()
                    currentScreen = Screen.ConversationList
                }
            )
        }

        is Screen.NewMessage -> {
            val newMessageViewModel: NewMessageViewModel = viewModel()
            val newMsgState by newMessageViewModel.uiState.collectAsStateWithLifecycle()

            NewMessageScreen(
                state = newMsgState,
                onBackClicked = { currentScreen = Screen.ConversationList },
                onQueryChanged = { newMessageViewModel.onQueryChanged(it) },
                onSelectContact = { address ->
                    newMessageViewModel.startConversation(address) { convId, cleanAddress ->
                        currentScreen = Screen.Chat(convId, cleanAddress)
                    }
                }
            )
        }

        is Screen.ScheduledMessages -> {
            val repository = SalimApplication.instance.smsRepository
            val scheduledMessages by repository.getScheduledMessages().collectAsState(initial = emptyList())

            ScheduledMessagesScreen(
                scheduledMessages = scheduledMessages,
                onBackClicked = { currentScreen = Screen.ConversationList },
                onSendNow = { messageId ->
                    conversationListViewModel.retryMessage(messageId)
                },
                onDeleteScheduled = { messageId, conversationId ->
                    conversationListViewModel.deleteMessage(messageId, conversationId)
                },
                onOpenConversation = { convId, address ->
                    currentScreen = Screen.Chat(convId, address)
                }
            )
        }

        is Screen.BlockedNumbers -> {
            val repository = SalimApplication.instance.smsRepository
            val blockedNumbers by repository.getBlockedNumbers().collectAsState(initial = emptyList())

            BlockedNumbersScreen(
                blockedNumbers = blockedNumbers,
                onBackClicked = { currentScreen = Screen.ConversationList },
                onUnblockClicked = { phone ->
                    conversationListViewModel.unblockNumber(phone)
                },
                onAddBlockNumber = { phone, name ->
                    val dummyConv = com.example.data.model.ConversationEntity(
                        recipientAddress = phone,
                        recipientName = name
                    )
                    conversationListViewModel.toggleSpam(dummyConv)
                }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                isDefaultSmsApp = convListState.isDefaultApp,
                carrierInfo = convListState.carrierInfo,
                hasSmsPermission = convListState.hasSmsPermission,
                hasContactsPermission = convListState.hasContactsPermission,
                onBackClicked = { currentScreen = Screen.ConversationList },
                onSetDefaultClicked = requestDefaultSmsApp,
                onRequestSmsPermission = requestAllSmsPermissions,
                onRequestContactsPermission = requestContactsPermission,
                onSeedDemoChats = {
                    conversationListViewModel.seedDemoConversations()
                }
            )
        }
    }
}
