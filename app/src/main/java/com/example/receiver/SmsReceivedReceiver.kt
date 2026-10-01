package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.SalimApplication
import com.example.telephony.TelephonyRoleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // If app is default, SmsDeliverReceiver already handled it
        if (TelephonyRoleHelper.isDefaultSmsApp(context)) return

        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val repository = SalimApplication.instance.smsRepository
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"
                val bodyBuilder = StringBuilder()
                var timestamp = System.currentTimeMillis()

                for (msg in messages) {
                    bodyBuilder.append(msg.messageBody)
                    if (msg.timestampMillis > 0) {
                        timestamp = msg.timestampMillis
                    }
                }

                repository.onIncomingSmsReceived(
                    senderAddress = sender,
                    body = bodyBuilder.toString(),
                    timestamp = timestamp
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
