package com.example.ui.conversationlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SalimApplication
import com.example.data.model.ConversationEntity
import com.example.telephony.TelephonyRoleHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConversationTab {
    INBOX,
    UNREAD,
    PINNED,
    ARCHIVED,
    SPAM
}

data class ConversationListUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val activeTab: ConversationTab = ConversationTab.INBOX,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isDefaultApp: Boolean = false,
    val showDefaultBanner: Boolean = true,
    val hasSmsPermission: Boolean = true,
    val hasContactsPermission: Boolean = true,
    val archivedCount: Int = 0,
    val spamCount: Int = 0,
    val carrierInfo: String = "Cellular"
)

private data class FilterState(
    val tab: ConversationTab,
    val query: String,
    val isSearching: Boolean,
    val dismissedBanner: Boolean
)

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalimApplication).smsRepository

    private val _activeTab = MutableStateFlow(ConversationTab.INBOX)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _dismissedDefaultBanner = MutableStateFlow(false)
    private val _permissionsRefresh = MutableStateFlow(System.currentTimeMillis())

    private val filterStateFlow = combine(
        _activeTab,
        _searchQuery,
        _isSearching,
        _dismissedDefaultBanner
    ) { tab, query, isSearching, dismissedBanner ->
        FilterState(tab, query, isSearching, dismissedBanner)
    }

    private val countsFlow = combine(
        repository.getArchivedCount(),
        repository.getSpamCount()
    ) { archived, spam ->
        Pair(archived, spam)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConversationListUiState> = combine(
        filterStateFlow,
        countsFlow,
        _permissionsRefresh
    ) { filter, counts, _ ->
        Pair(filter, counts)
    }.flatMapLatest { (filter, counts) ->
        val rawConversationsFlow = when {
            filter.query.isNotBlank() -> repository.searchConversations(filter.query.trim())
            filter.tab == ConversationTab.ARCHIVED -> repository.getConversations(isArchived = true, isSpam = false)
            filter.tab == ConversationTab.SPAM -> repository.getConversations(isArchived = false, isSpam = true)
            else -> repository.getConversations(isArchived = false, isSpam = false)
        }

        rawConversationsFlow.combine(MutableStateFlow(filter)) { convs, currentFilter ->
            val context = getApplication<Application>()
            val isDefault = TelephonyRoleHelper.isDefaultSmsApp(context)
            val hasSms = TelephonyRoleHelper.hasSmsPermissions(context)
            val hasContacts = TelephonyRoleHelper.hasContactsPermission(context)
            val carrier = TelephonyRoleHelper.getSimCarrierInfo(context)

            val filtered = when (currentFilter.tab) {
                ConversationTab.UNREAD -> convs.filter { it.unreadCount > 0 }
                ConversationTab.PINNED -> convs.filter { it.isPinned }
                else -> convs
            }

            ConversationListUiState(
                conversations = filtered,
                activeTab = currentFilter.tab,
                searchQuery = currentFilter.query,
                isSearching = currentFilter.isSearching,
                isDefaultApp = isDefault,
                showDefaultBanner = !isDefault && !currentFilter.dismissedBanner,
                hasSmsPermission = hasSms,
                hasContactsPermission = hasContacts,
                archivedCount = counts.first,
                spamCount = counts.second,
                carrierInfo = carrier
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversationListUiState()
    )

    fun setTab(tab: ConversationTab) {
        _activeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setIsSearching(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) {
            _searchQuery.value = ""
        }
    }

    fun dismissDefaultBanner() {
        _dismissedDefaultBanner.value = true
    }

    fun refreshPermissions() {
        _permissionsRefresh.value = System.currentTimeMillis()
    }

    fun togglePin(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.togglePin(conversation.id, conversation.isPinned)
        }
    }

    fun toggleArchive(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.toggleArchive(conversation.id, conversation.isArchived)
        }
    }

    fun toggleSpam(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.toggleSpam(
                conversation.id,
                conversation.isSpam,
                conversation.recipientAddress,
                conversation.recipientName
            )
        }
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            repository.unblockNumber(phoneNumber)
        }
    }

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            repository.retrySendMessage(messageId)
        }
    }

    fun deleteMessage(messageId: Long, conversationId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId, conversationId)
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }

    fun simulateIncomingSms(sender: String, message: String) {
        viewModelScope.launch {
            repository.onIncomingSmsReceived(sender, message)
        }
    }

    fun seedDemoConversations() {
        viewModelScope.launch {
            repository.seedDemoDataIfEmpty()
        }
    }
}
