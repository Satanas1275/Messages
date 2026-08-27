package com.example.liquidmessages

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, message.originatingAddress)
                put(Telephony.Sms.BODY, message.messageBody)
                put(Telephony.Sms.DATE, message.timestampMillis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        }
    }
}
