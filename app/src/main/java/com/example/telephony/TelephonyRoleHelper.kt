package com.example.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object TelephonyRoleHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return defaultPackage != null && defaultPackage == context.packageName
    }

    fun createDefaultSmsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                fallbackDefaultSmsIntent(context)
            }
        } else {
            fallbackDefaultSmsIntent(context)
        }
    }

    private fun fallbackDefaultSmsIntent(context: Context): Intent {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        return intent
    }

    fun hasSmsPermissions(context: Context): Boolean {
        val sendSms = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val readSms = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receiveSms = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return sendSms && readSms && receiveSms
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getSimCarrierInfo(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simState = tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
            val operatorName = tm?.simOperatorName?.takeIf { it.isNotBlank() }
                ?: tm?.networkOperatorName?.takeIf { it.isNotBlank() }

            when (simState) {
                TelephonyManager.SIM_STATE_READY -> operatorName ?: "Carrier Active"
                TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card Inserted"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN Required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK Required"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                else -> operatorName ?: "Cellular Available"
            }
        } catch (e: Exception) {
            "Cellular Telephony"
        }
    }
}
