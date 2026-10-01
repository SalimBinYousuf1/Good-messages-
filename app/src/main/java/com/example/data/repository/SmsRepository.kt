package com.example.data.repository

import android.content.Context
import com.example.data.local.BlockedNumberDao
import com.example.data.local.ConversationDao
import com.example.data.local.MessageDao
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.notification.NotificationHelper
import com.example.telephony.SmsSender
import com.example.telephony.TelephonyRoleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SmsRepository(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val contactsRepository: ContactsRepository,
    private val smsSender: SmsSender,
    private val systemSmsSync: SystemSmsSync? = null
) {

    suspend fun syncRealSystemMessages(): Int = withContext(Dispatchers.IO) {
        systemSmsSync?.syncSystemMessages() ?: 0
    }

    fun getConversations(isArchived: Boolean = false, isSpam: Boolean = false): Flow<List<ConversationEntity>> {
        return conversationDao.getConversations(isArchived, isSpam)
    }

    fun getConversationById(id: Long): Flow<ConversationEntity?> {
        return conversationDao.getConversationById(id)
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    fun getScheduledMessages(): Flow<List<MessageEntity>> {
        return messageDao.getScheduledMessages()
    }

    fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>> {
        return blockedNumberDao.getAllBlockedNumbers()
    }

    fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return conversationDao.searchConversations(query)
    }

    fun searchMessages(query: String): Flow<List<MessageEntity>> {
        return messageDao.searchMessages(query)
    }

    fun getArchivedCount(): Flow<Int> = conversationDao.getArchivedCount()
    fun getSpamCount(): Flow<Int> = conversationDao.getSpamCount()

    suspend fun getOrCreateConversation(recipientAddress: String): ConversationEntity = withContext(Dispatchers.IO) {
        val cleanAddress = ContactsRepository.normalizePhoneNumber(recipientAddress)
        val existing = conversationDao.findConversationByRecipientSync(cleanAddress)
        if (existing != null) {
            return@withContext existing
        }

        val contactName = contactsRepository.resolveContactName(cleanAddress)
        val colors = listOf("#2563EB", "#7C3AED", "#DB2777", "#059669", "#D97706", "#0891B2")
        val color = colors[kotlin.math.abs(cleanAddress.hashCode()) % colors.size]

        val newConv = ConversationEntity(
            recipientAddress = cleanAddress,
            recipientName = contactName,
            lastMessageSnippet = "",
            lastMessageTimestamp = System.currentTimeMillis(),
            avatarColorHex = color
        )
        val id = conversationDao.insertConversation(newConv)
        newConv.copy(id = id)
    }

    suspend fun sendMessage(
        conversationId: Long,
        recipientAddress: String,
        body: String,
        attachmentUri: String? = null,
        scheduledTime: Long? = null
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        val isScheduled = scheduledTime != null && scheduledTime > System.currentTimeMillis()

        val initialStatus = if (isScheduled) {
            MessageStatus.DRAFT
        } else {
            MessageStatus.SENDING
        }

        val message = MessageEntity(
            conversationId = conversationId,
            senderAddress = "Me",
            recipientAddress = recipientAddress,
            body = body.trim(),
            timestamp = System.currentTimeMillis(),
            isIncoming = false,
            status = initialStatus,
            isRead = true,
            isMms = attachmentUri != null,
            attachmentUri = attachmentUri,
            isScheduled = isScheduled,
            scheduledTime = scheduledTime
        )

        val messageId = messageDao.insertMessage(message)
        val savedMessage = message.copy(id = messageId)

        if (isScheduled) {
            conversationDao.updateLastMessageOutgoing(
                conversationId,
                "🕒 Scheduled: ${body.take(40)}",
                System.currentTimeMillis()
            )
            return@withContext Result.success(savedMessage)
        }

        conversationDao.updateLastMessageOutgoing(
            conversationId,
            body.take(60),
            System.currentTimeMillis()
        )

        // Dispatch via SmsSender
        val sendResult = smsSender.sendSms(messageId, recipientAddress, body)
        if (sendResult.isSuccess) {
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
            Result.success(savedMessage.copy(status = MessageStatus.SENT))
        } else {
            val errorMsg = sendResult.exceptionOrNull()?.message ?: "Failed to transmit SMS"
            messageDao.updateMessageStatus(messageId, MessageStatus.FAILED, errorMsg)
            Result.failure(sendResult.exceptionOrNull() ?: Exception(errorMsg))
        }
    }

    suspend fun retrySendMessage(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val message = messageDao.getMessageByIdSync(messageId)
            ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

        messageDao.updateMessageStatus(messageId, MessageStatus.SENDING, null)
        val sendResult = smsSender.sendSms(messageId, message.recipientAddress, message.body)
        if (sendResult.isSuccess) {
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
            Result.success(Unit)
        } else {
            val error = sendResult.exceptionOrNull()?.message ?: "Retry failed"
            messageDao.updateMessageStatus(messageId, MessageStatus.FAILED, error)
            Result.failure(Exception(error))
        }
    }

    suspend fun onIncomingSmsReceived(
        senderAddress: String,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val cleanAddress = ContactsRepository.normalizePhoneNumber(senderAddress)

        // Check if number is blocked
        if (blockedNumberDao.isNumberBlockedSync(cleanAddress) > 0) {
            // Blocked number, mark conversation or ignore
            val conv = getOrCreateConversation(cleanAddress)
            conversationDao.setSpam(conv.id, true)
            return@withContext
        }

        val conv = getOrCreateConversation(cleanAddress)

        val message = MessageEntity(
            conversationId = conv.id,
            senderAddress = cleanAddress,
            recipientAddress = "Me",
            body = body,
            timestamp = timestamp,
            isIncoming = true,
            status = MessageStatus.RECEIVED,
            isRead = false
        )

        messageDao.insertMessage(message)
        conversationDao.updateLastMessageIncoming(conv.id, body.take(60), timestamp)

        // Trigger notification
        val displayName = conv.recipientName ?: cleanAddress
        NotificationHelper.showIncomingMessageNotification(
            context,
            displayName,
            body,
            conv.id
        )
    }

    suspend fun updateMessageDeliveryStatus(messageId: Long, status: MessageStatus, error: String? = null) {
        withContext(Dispatchers.IO) {
            messageDao.updateMessageStatus(messageId, status, error)
        }
    }

    suspend fun markConversationAsRead(conversationId: Long) = withContext(Dispatchers.IO) {
        conversationDao.resetUnreadCount(conversationId)
        messageDao.markMessagesAsRead(conversationId)
    }

    suspend fun togglePin(conversationId: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.setPinned(conversationId, !isPinned)
    }

    suspend fun toggleArchive(conversationId: Long, isArchived: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.setArchived(conversationId, !isArchived)
    }

    suspend fun toggleSpam(conversationId: Long, isSpam: Boolean, address: String, name: String?) = withContext(Dispatchers.IO) {
        conversationDao.setSpam(conversationId, !isSpam)
        if (!isSpam) {
            // Add to blocked numbers
            blockedNumberDao.insertBlockedNumber(
                BlockedNumberEntity(phoneNumber = address, contactName = name)
            )
        } else {
            blockedNumberDao.deleteBlockedNumber(address)
        }
    }

    suspend fun deleteConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun deleteMessage(messageId: Long, conversationId: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
        val latest = messageDao.getLatestMessageForConversation(conversationId)
        if (latest != null) {
            conversationDao.updateLastMessageOutgoing(conversationId, latest.body.take(60), latest.timestamp)
        } else {
            conversationDao.updateLastMessageOutgoing(conversationId, "", System.currentTimeMillis())
        }
    }

    suspend fun saveDraft(conversationId: Long, draft: String) = withContext(Dispatchers.IO) {
        conversationDao.updateDraft(conversationId, draft)
    }

    suspend fun unblockNumber(phoneNumber: String) = withContext(Dispatchers.IO) {
        blockedNumberDao.deleteBlockedNumber(phoneNumber)
        val conv = conversationDao.findConversationByRecipientSync(phoneNumber)
        if (conv != null) {
            conversationDao.setSpam(conv.id, false)
        }
    }

    suspend fun sendDueScheduledMessages() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dueMessages = messageDao.getDueScheduledMessages(now)
        for (msg in dueMessages) {
            messageDao.updateMessageStatus(msg.id, MessageStatus.SENDING)
            val res = smsSender.sendSms(msg.id, msg.recipientAddress, msg.body)
            if (res.isSuccess) {
                messageDao.updateMessage(msg.copy(status = MessageStatus.SENT, isScheduled = false))
            } else {
                messageDao.updateMessage(
                    msg.copy(
                        status = MessageStatus.FAILED,
                        isScheduled = false,
                        errorMessage = res.exceptionOrNull()?.message
                    )
                )
            }
        }
    }

    suspend fun seedDemoDataIfEmpty() = withContext(Dispatchers.IO) {
        val existing = conversationDao.findConversationByRecipientSync("+1 (555) 234-5678")
        if (existing != null) return@withContext

        // Create sample conversations for immediate first-launch experience
        val conv1 = ConversationEntity(
            recipientAddress = "+1 (555) 234-5678",
            recipientName = "Maya Lin",
            lastMessageSnippet = "Sounds great! See you tomorrow at the cafe.",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 15, // 15 mins ago
            unreadCount = 1,
            isPinned = true,
            avatarColorHex = "#2563EB"
        )
        val id1 = conversationDao.insertConversation(conv1)
        messageDao.insertMessage(
            MessageEntity(
                conversationId = id1,
                senderAddress = "+1 (555) 234-5678",
                recipientAddress = "Me",
                body = "Hey Salim, are we still meeting to discuss the project?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                isIncoming = true,
                status = MessageStatus.RECEIVED,
                isRead = true
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                conversationId = id1,
                senderAddress = "Me",
                recipientAddress = "+1 (555) 234-5678",
                body = "Yes absolutely! Let's do 10 AM at the corner cafe.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 30,
                isIncoming = false,
                status = MessageStatus.DELIVERED,
                isRead = true
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                conversationId = id1,
                senderAddress = "+1 (555) 234-5678",
                recipientAddress = "Me",
                body = "Sounds great! See you tomorrow at the cafe.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                isIncoming = true,
                status = MessageStatus.RECEIVED,
                isRead = false
            )
        )

        val conv2 = ConversationEntity(
            recipientAddress = "+1 (555) 987-6543",
            recipientName = "Alexander Wright",
            lastMessageSnippet = "Can you send over the updated documents?",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 180, // 3 hours ago
            unreadCount = 0,
            isPinned = false,
            avatarColorHex = "#7C3AED"
        )
        val id2 = conversationDao.insertConversation(conv2)
        messageDao.insertMessage(
            MessageEntity(
                conversationId = id2,
                senderAddress = "+1 (555) 987-6543",
                recipientAddress = "Me",
                body = "Can you send over the updated documents?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 180,
                isIncoming = true,
                status = MessageStatus.RECEIVED,
                isRead = true
            )
        )

        val conv3 = ConversationEntity(
            recipientAddress = "88900",
            recipientName = "Bank Security Alerts",
            lastMessageSnippet = "Your verification code is 482910. Do not share.",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24, // 1 day ago
            unreadCount = 0,
            isPinned = false,
            avatarColorHex = "#059669"
        )
        val id3 = conversationDao.insertConversation(conv3)
        messageDao.insertMessage(
            MessageEntity(
                conversationId = id3,
                senderAddress = "88900",
                recipientAddress = "Me",
                body = "Your verification code is 482910. Do not share with anyone. Expires in 10 minutes.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24,
                isIncoming = true,
                status = MessageStatus.RECEIVED,
                isRead = true
            )
        )
    }
}
