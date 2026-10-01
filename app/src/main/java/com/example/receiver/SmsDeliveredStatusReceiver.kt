package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SalimApplication
import com.example.data.model.MessageStatus
import com.example.telephony.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveredStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val messageId = intent?.getLongExtra(SmsSender.EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (messageId == -1L) return

        val repository = SalimApplication.instance.smsRepository
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (resultCode == Activity.RESULT_OK) {
                    repository.updateMessageDeliveryStatus(messageId, MessageStatus.DELIVERED)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
