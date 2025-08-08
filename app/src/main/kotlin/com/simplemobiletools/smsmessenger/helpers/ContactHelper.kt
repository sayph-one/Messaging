package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.ContactsContract
import com.simplemobiletools.commons.helpers.SimpleContactsHelper

class ContactFilterHelper(private val context: Context) {

    private val simpleContactsHelper = SimpleContactsHelper(context)

    fun isContactSaved(phoneNumber: String): Boolean {
        if (phoneNumber.isEmpty()) return false

        // Normalize the phone number for comparison
        val normalizedNumber = normalizePhoneNumber(phoneNumber)

        // Check if contact exists in the contacts database
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalizedNumber)
            .build()

        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        return phoneNumber.replace(Regex("[^\\d+]"), "")
    }
}
