package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
        Index(value = ["senderAddress"]),
        Index(value = ["recipientAddress"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val senderAddress: String,
    val recipientAddress: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val status: MessageStatus = if (isIncoming) MessageStatus.RECEIVED else MessageStatus.SENT,
    val isRead: Boolean = !isIncoming,
    val isMms: Boolean = false,
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
    val errorMessage: String? = null,
    val isScheduled: Boolean = false,
    val scheduledTime: Long? = null
)
