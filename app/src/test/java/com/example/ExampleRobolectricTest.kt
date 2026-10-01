package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SalimDatabase
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.data.repository.ContactsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: SalimDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SalimDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context validates Salim app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Salim", appName)
    }

    @Test
    fun `phone number normalization removes non-numeric characters`() {
        val raw = "+1 (555) 123-4567"
        val normalized = ContactsRepository.normalizePhoneNumber(raw)
        assertEquals("+15551234567", normalized)
    }

    @Test
    fun `insert and retrieve conversation in database`() = runBlocking {
        val conversationDao = database.conversationDao()
        val conv = ConversationEntity(
            recipientAddress = "+15559998888",
            recipientName = "Zaid",
            lastMessageSnippet = "Hello there",
            unreadCount = 2,
            isPinned = true
        )
        val id = conversationDao.insertConversation(conv)
        assertTrue(id > 0)

        val retrieved = conversationDao.findConversationByRecipientSync("+15559998888")
        assertNotNull(retrieved)
        assertEquals("Zaid", retrieved?.recipientName)
        assertEquals(2, retrieved?.unreadCount)
        assertTrue(retrieved?.isPinned == true)
    }

    @Test
    fun `insert and update message status`() = runBlocking {
        val messageDao = database.messageDao()
        val msg = MessageEntity(
            conversationId = 1L,
            senderAddress = "Me",
            recipientAddress = "+15559998888",
            body = "Testing Salim SMS",
            isIncoming = false,
            status = MessageStatus.SENDING
        )
        val id = messageDao.insertMessage(msg)
        assertTrue(id > 0)

        // Update status to DELIVERED
        messageDao.updateMessageStatus(id, MessageStatus.DELIVERED)
        val updated = messageDao.getMessageByIdSync(id)
        assertNotNull(updated)
        assertEquals(MessageStatus.DELIVERED, updated?.status)
    }
}
