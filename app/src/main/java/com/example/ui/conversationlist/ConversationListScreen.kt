package com.example.ui.conversationlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DefaultAppBanner
import com.example.ui.components.PermissionWarningBanner
import com.example.ui.simulator.SmsSimulatorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    onTabSelected: (ConversationTab) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSetIsSearching: (Boolean) -> Unit,
    onConversationClicked: (Long, String) -> Unit,
    onTogglePin: (com.example.data.model.ConversationEntity) -> Unit,
    onToggleArchive: (com.example.data.model.ConversationEntity) -> Unit,
    onToggleSpam: (com.example.data.model.ConversationEntity) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onComposeClicked: () -> Unit,
    onSetDefaultClicked: () -> Unit,
    onDismissDefaultBanner: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onRequestContactsPermissions: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
    onSimulateIncomingSms: (String, String) -> Unit,
    onSeedDemoChats: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSimulatorDialog by remember { mutableStateOf(false) }

    if (showSimulatorDialog) {
        SmsSimulatorDialog(
            onDismiss = { showSimulatorDialog = false },
            onSimulateIncomingSms = onSimulateIncomingSms
        )
    }

    Scaffold(
        topBar = {
            if (state.isSearching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search messages, names, numbers...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field"),
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onSetIsSearching(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Salim",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = state.carrierInfo,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onSetIsSearching(true) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("overflow_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Scheduled Messages") },
                                onClick = {
                                    showMenu = false
                                    onOpenScheduled()
                                },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Blocked & Spam (${state.spamCount})") },
                                onClick = {
                                    showMenu = false
                                    onOpenBlocked()
                                },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Archived (${state.archivedCount})") },
                                onClick = {
                                    showMenu = false
                                    onTabSelected(ConversationTab.ARCHIVED)
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Simulate Inbound SMS") },
                                onClick = {
                                    showMenu = false
                                    showSimulatorDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Seed Sample Chats") },
                                onClick = {
                                    showMenu = false
                                    onSeedDemoChats()
                                },
                                leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Settings & Default App") },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onComposeClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("compose_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Message",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Permission Warnings
            if (!state.hasSmsPermission) {
                PermissionWarningBanner(
                    title = "SMS Permissions Required",
                    description = "Grant SMS permissions to view, receive, and send text messages.",
                    onGrantClicked = onRequestSmsPermissions
                )
            } else if (!state.hasContactsPermission) {
                PermissionWarningBanner(
                    title = "Contacts Access",
                    description = "Allow contacts access to display contact names and pictures.",
                    onGrantClicked = onRequestContactsPermissions
                )
            }

            // Default App Banner
            if (state.showDefaultBanner) {
                DefaultAppBanner(
                    onSetDefaultClicked = onSetDefaultClicked,
                    onDismissClicked = onDismissDefaultBanner
                )
            }

            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("filter_chips_row")
            ) {
                item {
                    FilterChip(
                        selected = state.activeTab == ConversationTab.INBOX,
                        onClick = { onTabSelected(ConversationTab.INBOX) },
                        label = { Text("All Messages") },
                        modifier = Modifier.padding(end = 8.dp).testTag("tab_all")
                    )
                }
                item {
                    FilterChip(
                        selected = state.activeTab == ConversationTab.UNREAD,
                        onClick = { onTabSelected(ConversationTab.UNREAD) },
                        label = { Text("Unread") },
                        modifier = Modifier.padding(end = 8.dp).testTag("tab_unread")
                    )
                }
                item {
                    FilterChip(
                        selected = state.activeTab == ConversationTab.PINNED,
                        onClick = { onTabSelected(ConversationTab.PINNED) },
                        label = { Text("Pinned") },
                        modifier = Modifier.padding(end = 8.dp).testTag("tab_pinned")
                    )
                }
                item {
                    FilterChip(
                        selected = state.activeTab == ConversationTab.ARCHIVED,
                        onClick = { onTabSelected(ConversationTab.ARCHIVED) },
                        label = { Text("Archived (${state.archivedCount})") },
                        modifier = Modifier.padding(end = 8.dp).testTag("tab_archived")
                    )
                }
                item {
                    FilterChip(
                        selected = state.activeTab == ConversationTab.SPAM,
                        onClick = { onTabSelected(ConversationTab.SPAM) },
                        label = { Text("Spam (${state.spamCount})") },
                        modifier = Modifier.testTag("tab_spam")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            if (state.conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = when (state.activeTab) {
                                ConversationTab.ARCHIVED -> Icons.Default.Archive
                                ConversationTab.SPAM -> Icons.Default.Block
                                else -> Icons.Default.ChatBubbleOutline
                            },
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                state.searchQuery.isNotEmpty() -> "No messages matching \"${state.searchQuery}\""
                                state.activeTab == ConversationTab.UNREAD -> "No unread messages"
                                state.activeTab == ConversationTab.PINNED -> "No pinned conversations"
                                state.activeTab == ConversationTab.ARCHIVED -> "No archived conversations"
                                state.activeTab == ConversationTab.SPAM -> "No spam or blocked messages"
                                else -> "No conversations yet"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                state.searchQuery.isNotEmpty() -> "Try searching for a different keyword or phone number"
                                state.activeTab == ConversationTab.ARCHIVED -> "Conversations you archive will appear here"
                                state.activeTab == ConversationTab.SPAM -> "Messages from blocked contacts will be moved here"
                                else -> "Tap the compose button below to start your first chat!"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("conversations_list")
                ) {
                    items(
                        items = state.conversations,
                        key = { it.id }
                    ) { item ->
                        ConversationListItem(
                            conversation = item,
                            onClick = { onConversationClicked(item.id, item.recipientAddress) },
                            onTogglePin = { onTogglePin(item) },
                            onToggleArchive = { onToggleArchive(item) },
                            onToggleSpam = { onToggleSpam(item) },
                            onDelete = { onDeleteConversation(item.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 78.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
