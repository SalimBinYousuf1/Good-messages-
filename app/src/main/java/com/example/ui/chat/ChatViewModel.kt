package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SalimApplication
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val attachmentUri: String? = null,
    val scheduledTimeMillis: Long? = null,
    val previewMediaUri: String? = null,
    val isSending: Boolean = false,
    val smsCharCount: Int = 0,
    val smsSegmentCount: Int = 1,
    val errorMessage: String? = null
)

private data class InputDraftState(
    val text: String,
    val attachmentUri: String?,
    val scheduledTimeMillis: Long?,
    val previewMediaUri: String?
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalimApplication).smsRepository

    private val _conversationId = MutableStateFlow<Long>(-1L)
    private val _recipientAddress = MutableStateFlow("")
    private val _inputText = MutableStateFlow("")
    private val _attachmentUri = MutableStateFlow<String?>(null)
    private val _scheduledTimeMillis = MutableStateFlow<Long?>(null)
    private val _previewMediaUri = MutableStateFlow<String?>(null)
    private val _isSending = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    fun initialize(conversationId: Long, recipientAddress: String) {
        _conversationId.value = conversationId
        _recipientAddress.value = recipientAddress

        viewModelScope.launch {
            if (conversationId > 0) {
                repository.markConversationAsRead(conversationId)
            } else if (recipientAddress.isNotBlank()) {
                val conv = repository.getOrCreateConversation(recipientAddress)
                _conversationId.value = conv.id
                repository.markConversationAsRead(conv.id)
            }
        }
    }

    private val inputDraftFlow = combine(
        _inputText,
        _attachmentUri,
        _scheduledTimeMillis,
        _previewMediaUri
    ) { text, attachment, scheduled, preview ->
        InputDraftState(text, attachment, scheduled, preview)
    }

    private val statusFlow = combine(
        _isSending,
        _errorMessage
    ) { sending, error ->
        Pair(sending, error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = _conversationId.flatMapLatest { id ->
        if (id <= 0) {
            flowOf(ChatUiState())
        } else {
            combine(
                repository.getConversationById(id),
                repository.getMessagesForConversation(id),
                inputDraftFlow,
                statusFlow
            ) { conv, msgs, draft, status ->
                val chars = draft.text.length
                val segments = when {
                    chars == 0 -> 1
                    chars <= 160 -> 1
                    else -> ((chars - 1) / 153) + 1
                }

                ChatUiState(
                    conversation = conv,
                    messages = msgs,
                    inputText = draft.text,
                    attachmentUri = draft.attachmentUri,
                    scheduledTimeMillis = draft.scheduledTimeMillis,
                    previewMediaUri = draft.previewMediaUri,
                    isSending = status.first,
                    smsCharCount = chars,
                    smsSegmentCount = segments,
                    errorMessage = status.second
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputTextChanged(text: String) {
        _inputText.value = text
        val convId = _conversationId.value
        if (convId > 0) {
            viewModelScope.launch {
                repository.saveDraft(convId, text)
            }
        }
    }

    fun onSelectQuickReply(reply: String) {
        val current = _inputText.value
        _inputText.value = if (current.isBlank()) reply else "$current $reply"
    }

    fun setAttachmentUri(uri: String?) {
        _attachmentUri.value = uri
    }

    fun setScheduledTime(timeMillis: Long?) {
        _scheduledTimeMillis.value = timeMillis
    }

    fun setPreviewMediaUri(uri: String?) {
        _previewMediaUri.value = uri
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val attachment = _attachmentUri.value
        val scheduledTime = _scheduledTimeMillis.value

        if (text.isBlank() && attachment == null) return

        val convId = _conversationId.value
        val address = uiState.value.conversation?.recipientAddress ?: _recipientAddress.value
        if (convId <= 0 || address.isBlank()) return

        _isSending.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repository.sendMessage(
                conversationId = convId,
                recipientAddress = address,
                body = text,
                attachmentUri = attachment,
                scheduledTime = scheduledTime
            )

            _isSending.value = false
            if (result.isSuccess) {
                _inputText.value = ""
                _attachmentUri.value = null
                _scheduledTimeMillis.value = null
                repository.saveDraft(convId, "")
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send SMS"
            }
        }
    }

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            repository.retrySendMessage(messageId)
        }
    }

    fun deleteMessage(messageId: Long) {
        val convId = _conversationId.value
        if (convId > 0) {
            viewModelScope.launch {
                repository.deleteMessage(messageId, convId)
            }
        }
    }

    fun deleteConversation() {
        val convId = _conversationId.value
        if (convId > 0) {
            viewModelScope.launch {
                repository.deleteConversation(convId)
            }
        }
    }

    fun toggleBlockContact() {
        val conv = uiState.value.conversation ?: return
        viewModelScope.launch {
            repository.toggleSpam(
                conv.id,
                conv.isSpam,
                conv.recipientAddress,
                conv.recipientName
            )
        }
    }
}
