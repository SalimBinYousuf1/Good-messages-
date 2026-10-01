package com.example.ui.newmessage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SalimApplication
import com.example.data.model.ContactItem
import com.example.data.repository.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewMessageUiState(
    val query: String = "",
    val allContacts: List<ContactItem> = emptyList(),
    val filteredContacts: List<ContactItem> = emptyList(),
    val isLoadingContacts: Boolean = false,
    val isPhoneNumberValid: Boolean = false
)

class NewMessageViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = (application as SalimApplication).contactsRepository
    private val smsRepository = (application as SalimApplication).smsRepository

    private val _query = MutableStateFlow("")
    private val _allContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<NewMessageUiState> = combine(
        _query,
        _allContacts,
        _isLoading
    ) { q, contacts, loading ->
        val trimmed = q.trim()
        val filtered = if (trimmed.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                it.phoneNumber.contains(trimmed)
            }
        }
        val cleanNumber = ContactsRepository.normalizePhoneNumber(trimmed)
        val isValidNumber = cleanNumber.length >= 3

        NewMessageUiState(
            query = q,
            allContacts = contacts,
            filteredContacts = filtered,
            isLoadingContacts = loading,
            isPhoneNumberValid = isValidNumber
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NewMessageUiState()
    )

    init {
        loadContacts()
    }

    fun loadContacts() {
        _isLoading.value = true
        viewModelScope.launch {
            val list = contactsRepository.getContacts()
            _allContacts.value = list
            _isLoading.value = false
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun startConversation(address: String, onConversationReady: (Long, String) -> Unit) {
        val clean = ContactsRepository.normalizePhoneNumber(address)
        if (clean.isBlank()) return

        viewModelScope.launch {
            val conv = smsRepository.getOrCreateConversation(clean)
            onConversationReady(conv.id, clean)
        }
    }
}
