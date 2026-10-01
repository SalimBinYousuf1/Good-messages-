package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // MMS WAP Push receiver stub required for default SMS app compliance
        // If data is present, can be processed or downloaded
    }
}
