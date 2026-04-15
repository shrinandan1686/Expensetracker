package com.trackit.expense.util

import android.content.Context
import android.provider.Telephony
import com.trackit.expense.sms.SmsReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads historical SMS messages from the device inbox via [ContentResolver].
 *
 * Only messages from [SmsReceiver.KNOWN_SENDERS] (bank / UPI gateways) are
 * returned — unknown senders are filtered out here so the caller never sees them.
 *
 * A [SecurityException] (READ_SMS not granted) is caught and returns an empty list;
 * the caller should check the permission before invoking [readSince].
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class RawSms(val body: String, val timestamp: Long, val sender: String)

    fun readSince(fromMillis: Long): List<RawSms> {
        val results = mutableListOf<RawSms>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.Inbox.BODY,
                    Telephony.Sms.Inbox.DATE,
                    Telephony.Sms.Inbox.ADDRESS
                ),
                "${Telephony.Sms.Inbox.DATE} >= ?",
                arrayOf(fromMillis.toString()),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )?.use { cursor ->
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)

                while (cursor.moveToNext()) {
                    val body   = cursor.getString(bodyIdx) ?: continue
                    val date   = cursor.getLong(dateIdx)
                    val sender = cursor.getString(addrIdx) ?: continue

                    // Strip country/operator prefix (e.g. "IN-HDFCBK" → "HDFCBK")
                    val normalized = sender.uppercase()
                        .replace(Regex("^[A-Z]{2}-"), "")
                        .trim()

                    if (normalized in SmsReceiver.KNOWN_SENDERS) {
                        results.add(RawSms(body, date, sender))
                    }
                }
            }
        } catch (_: SecurityException) {
            // READ_SMS not granted — caller should have checked permission first
        } catch (_: Exception) {
            // ContentProvider unavailable on some devices
        }
        return results
    }
}
