package com.zfdang.chess.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import org.petero.droidfish.utils.SettingUtils

typealias CheckmateSmsMessageHandler = (String) -> Unit
//短信广播
@Suppress("PrivatePropertyName", "UNUSED_PARAMETER")
class CheckmateSmsReceiver : BroadcastReceiver() {
    companion object {
        @JvmStatic
        var onNewMessage: CheckmateSmsMessageHandler = {}
        @JvmStatic
        private var enableReceiving = false

        @JvmStatic
        fun startReceiving() {
            enableReceiving = true
        }

        @JvmStatic
        fun stopReceiving() {
            enableReceiving = false
            onNewMessage = {}
        }
    }
    private val TAG = CheckmateSmsReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (!enableReceiving) {
            return
        }
        try {
            //过滤广播
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                return

            val checkmatePhoneNumber = SettingUtils.checkMatePhoneNumber
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                .filter { smsMessage ->
                    smsMessage.displayOriginatingAddress.endsWith(
                        checkmatePhoneNumber
                    )
                }
                .sortedBy { it.timestampMillis }
            for (smsMessage in smsMessages) {
                val message = smsMessage.messageBody
                Log.d(TAG, "Found Checkmate message $message")
                onNewMessage(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing SMS failed: " + e.message.toString())
        }
    }
}