package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.SalimApplication
import com.example.data.model.MessageStatus
import com.example.telephony.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSentStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val messageId = intent?.getLongExtra(SmsSender.EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (messageId == -1L) return

        val repository = SalimApplication.instance.smsRepository
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resultCode = resultCode
                if (resultCode == Activity.RESULT_OK) {
                    repository.updateMessageDeliveryStatus(messageId, MessageStatus.SENT)
                } else {
                    val errorDescription = when (resultCode) {
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Transmission error (Generic failure)"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service / radio offline"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU received"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio turned off / Airplane mode"
                        else -> "Failed to send (Error code: $resultCode)"
                    }
                    repository.updateMessageDeliveryStatus(messageId, MessageStatus.FAILED, errorDescription)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
