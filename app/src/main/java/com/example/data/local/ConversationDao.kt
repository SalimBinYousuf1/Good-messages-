package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("""
        SELECT * FROM conversations 
        WHERE isArchived = :isArchived AND isSpam = :isSpam 
        ORDER BY isPinned DESC, lastMessageTimestamp DESC
    """)
    fun getConversations(isArchived: Boolean = false, isSpam: Boolean = false): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationByIdSync(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE recipientAddress = :address LIMIT 1")
    suspend fun findConversationByRecipientSync(address: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE recipientAddress = :address LIMIT 1")
    fun findConversationByRecipientFlow(address: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET draftText = :draft WHERE id = :id")
    suspend fun updateDraft(id: Long, draft: String)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean)

    @Query("UPDATE conversations SET isSpam = :isSpam WHERE id = :id")
    suspend fun setSpam(id: Long, isSpam: Boolean)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun resetUnreadCount(id: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1, lastMessageSnippet = :snippet, lastMessageTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastMessageIncoming(id: Long, snippet: String, timestamp: Long)

    @Query("UPDATE conversations SET lastMessageSnippet = :snippet, lastMessageTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastMessageOutgoing(id: Long, snippet: String, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("DELETE FROM conversations WHERE recipientAddress LIKE '%555%' OR recipientAddress = '88900'")
    suspend fun deleteDemoConversations()

    @Query("""
        SELECT * FROM conversations 
        WHERE (recipientName LIKE '%' || :query || '%' OR recipientAddress LIKE '%' || :query || '%' OR lastMessageSnippet LIKE '%' || :query || '%')
        AND isSpam = 0
        ORDER BY lastMessageTimestamp DESC
    """)
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 1")
    fun getArchivedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversations WHERE isSpam = 1")
    fun getSpamCount(): Flow<Int>
}
