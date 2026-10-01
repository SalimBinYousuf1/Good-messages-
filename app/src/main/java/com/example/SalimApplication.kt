package com.example

import android.app.Application
import com.example.data.local.SalimDatabase
import com.example.data.repository.ContactsRepository
import com.example.data.repository.SmsRepository
import com.example.data.repository.SystemSmsSync
import com.example.notification.NotificationHelper
import com.example.telephony.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SalimApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { SalimDatabase.getInstance(this) }
    val contactsRepository by lazy { ContactsRepository(this) }
    val smsSender by lazy { SmsSender(this) }
    val systemSmsSync by lazy {
        SystemSmsSync(
            context = this,
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            contactsRepository = contactsRepository
        )
    }

    val smsRepository by lazy {
        SmsRepository(
            context = this,
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            blockedNumberDao = database.blockedNumberDao(),
            contactsRepository = contactsRepository,
            smsSender = smsSender,
            systemSmsSync = systemSmsSync
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.createNotificationChannels(this)

        // Start real system SMS sync and real-time ContentObserver
        systemSmsSync.startObserving(applicationScope)
    }

    companion object {
        lateinit var instance: SalimApplication
            private set
    }
}
