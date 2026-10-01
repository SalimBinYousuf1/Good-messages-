package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_numbers",
    indices = [
        Index(value = ["phoneNumber"], unique = true)
    ]
)
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String? = null,
    val blockedAt: Long = System.currentTimeMillis(),
    val reason: String = "Spam or unwanted messages"
)
