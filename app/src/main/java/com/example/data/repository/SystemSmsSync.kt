package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.example.data.local.ConversationDao
import com.example.data.local.MessageDao
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.telephony.TelephonyRoleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemSmsSync(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val contactsRepository: ContactsRepository
) {

    companion object {
        private const val TAG = "SystemSmsSync"
    }

    private var contentObserver: ContentObserver? = null
    private var syncJob: Job? = null

    suspend fun syncSystemMessages(): Int = withContext(Dispatchers.IO) {
        if (!TelephonyRoleHelper.hasSmsPermissions(context)) {
            Log.d(TAG, "No SMS permissions to read system SMS.")
            return@withContext 0
        }

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS
            )

            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )

            if (cursor == null) {
                Log.d(TAG, "Null cursor returned from Telephony.Sms.CONTENT_URI")
                return@withContext 0
            }

            var importedCount = 0
            var hasRealMessages = false

            cursor.use { c ->
                val idCol = c.getColumnIndex(Telephony.Sms._ID)
                val addressCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
                val typeCol = c.getColumnIndex(Telephony.Sms.TYPE)
                val readCol = c.getColumnIndex(Telephony.Sms.READ)

                val conversationCache = mutableMapOf<String, ConversationEntity>()

                while (c.moveToNext()) {
                    val rawAddress = if (addressCol >= 0) c.getString(addressCol) else null
                    if (rawAddress.isNullOrBlank()) continue

                    val cleanAddress = ContactsRepository.normalizePhoneNumber(rawAddress)
                    val body = (if (bodyCol >= 0) c.getString(bodyCol) else "") ?: ""
                    val timestamp = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                    val type = if (typeCol >= 0) c.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = if (readCol >= 0) (c.getInt(readCol) == 1) else true

                    hasRealMessages = true

                    // Find or create conversation in cache/DB
                    var conv = conversationCache[cleanAddress]
                    if (conv == null) {
                        var existing = conversationDao.findConversationByRecipientSync(cleanAddress)
                        if (existing == null) {
                            val contactName = contactsRepository.resolveContactName(cleanAddress)
                            val colors = listOf("#2563EB", "#7C3AED", "#DB2777", "#059669", "#D97706", "#0891B2")
                            val color = colors[kotlin.math.abs(cleanAddress.hashCode()) % colors.size]

                            val newConv = ConversationEntity(
                                recipientAddress = cleanAddress,
                                recipientName = contactName,
                                lastMessageSnippet = body.take(60),
                                lastMessageTimestamp = timestamp,
                                unreadCount = if (!isRead && type == Telephony.Sms.MESSAGE_TYPE_INBOX) 1 else 0,
                                avatarColorHex = color
                            )
                            val newId = conversationDao.insertConversation(newConv)
                            existing = newConv.copy(id = newId)
                        }
                        conv = existing
                        conversationCache[cleanAddress] = existing
                    }

                    // Check if this message already exists in local DB
                    val exists = messageDao.messageExists(timestamp, body)
                    if (exists == 0) {
                        val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                        val messageEntity = MessageEntity(
                            conversationId = conv.id,
                            senderAddress = if (isIncoming) cleanAddress else "Me",
                            recipientAddress = if (isIncoming) "Me" else cleanAddress,
                            body = body,
                            timestamp = timestamp,
                            isIncoming = isIncoming,
                            status = if (isIncoming) MessageStatus.RECEIVED else MessageStatus.SENT,
                            isRead = isRead
                        )
                        messageDao.insertMessage(messageEntity)
                        importedCount++
                    }
                }
            }

            // If real messages were found, remove demo conversations to avoid confusing the user
            if (hasRealMessages) {
                conversationDao.deleteDemoConversations()
                messageDao.deleteDemoMessages()
            }

            Log.d(TAG, "System SMS sync complete. Imported $importedCount messages.")
            importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing system SMS", e)
            0
        }
    }

    fun startObserving(scope: CoroutineScope) {
        if (contentObserver != null) return

        val handler = Handler(Looper.getMainLooper())
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // Debounce sync
                syncJob?.cancel()
                syncJob = scope.launch(Dispatchers.IO) {
                    delay(400)
                    syncSystemMessages()
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                contentObserver!!
            )
            Log.d(TAG, "Registered ContentObserver for Telephony.Sms.CONTENT_URI")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register ContentObserver", e)
        }

        // Trigger immediate sync on start
        scope.launch(Dispatchers.IO) {
            syncSystemMessages()
        }
    }

    fun stopObserving() {
        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                // Ignore
            }
            contentObserver = null
        }
    }

    fun writeSentSmsToSystem(destinationAddress: String, text: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, destinationAddress)
                put(Telephony.Sms.BODY, text)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write sent SMS to Telephony.Sms.Sent", e)
            null
        }
    }
}
