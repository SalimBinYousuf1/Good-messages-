package com.example.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Required for default SMS app compliance to handle respond-via-message actions
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
