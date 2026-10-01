package com.example.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.example.data.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSender(private val context: Context) {

    companion object {
        const val ACTION_SMS_SENT = "com.example.salim.SMS_SENT_ACTION"
        const val ACTION_SMS_DELIVERED = "com.example.salim.SMS_DELIVERED_ACTION"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    suspend fun sendSms(
        messageId: Long,
        destinationAddress: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!TelephonyRoleHelper.hasSmsPermissions(context)) {
            return@withContext Result.failure(SecurityException("SMS permissions are not granted."))
        }

        try {
            val smsManager = getSmsManager()
            val parts = smsManager.divideMessage(text)

            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            for (i in parts.indices) {
                val sentIntent = Intent(ACTION_SMS_SENT).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_MESSAGE_ID, messageId)
                }
                val sentPi = PendingIntent.getBroadcast(
                    context,
                    (messageId * 100 + i).toInt(),
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                sentIntents.add(sentPi)

                val deliveryIntent = Intent(ACTION_SMS_DELIVERED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_MESSAGE_ID, messageId)
                }
                val deliveryPi = PendingIntent.getBroadcast(
                    context,
                    (messageId * 100 + i + 50).toInt(),
                    deliveryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                deliveryIntents.add(deliveryPi)
            }

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    text,
                    sentIntents.firstOrNull(),
                    deliveryIntents.firstOrNull()
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
