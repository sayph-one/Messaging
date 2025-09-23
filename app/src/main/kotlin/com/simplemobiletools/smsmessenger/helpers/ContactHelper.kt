package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.simplemobiletools.commons.helpers.SimpleContactsHelper

class ContactFilterHelper(private val context: Context) {

    private val simpleContactsHelper = SimpleContactsHelper(context)

    // Common SIM account types across different manufacturers
    private val simAccountTypes = setOf(
        "com.android.contacts.sim",     // Standard Android
        "vnd.sec.contact.sim",          // Samsung
        "com.android.sim",              // Some Android variants
        "sim",                          // Generic
        "SIM"                           // Case variant
    )

    fun isContactSaved(phoneNumber: String): Boolean {
        Log.d("ContactDebug", "=== Checking contact for number: $phoneNumber ===")

        if (phoneNumber.isEmpty()) {
            Log.d("ContactDebug", "Phone number is empty, returning false")
            return false
        }

        // Normalize the phone number for comparison
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        Log.d("ContactDebug", "Normalized number: $normalizedNumber")

        // First, get contact IDs that match the phone number
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalizedNumber)
            .build()
        Log.d("ContactDebug", "Lookup URI: $lookupUri")

        val contactIds = mutableSetOf<String>()

        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null
        )?.use { cursor ->
            Log.d("ContactDebug", "PhoneLookup query returned ${cursor.count} results")
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(0)
                contactIds.add(contactId)
                Log.d("ContactDebug", "Found contact ID: $contactId")
            }
        }

        if (contactIds.isEmpty()) {
            Log.d("ContactDebug", "No contact IDs found, returning false")
            return false
        }

        Log.d("ContactDebug", "Total contact IDs found: ${contactIds.size}")

        // Now check if any of these contacts are stored on device (not SIM)
        val contactIdsList = contactIds.joinToString(",")
        Log.d("ContactDebug", "Checking raw contacts for IDs: $contactIdsList")

        // First, let's see ALL raw contacts for these IDs to understand what we're dealing with
        var hasNonSimContact = false

        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($contactIdsList)",
            null,
            null
        )?.use { cursor ->
            Log.d("ContactDebug", "Raw contacts query returned ${cursor.count} results")
            while (cursor.moveToNext()) {
                val rawContactId = cursor.getString(0)
                val contactId = cursor.getString(1)
                val accountType = cursor.getString(2)
                val accountName = cursor.getString(3)

                Log.d("ContactDebug", "Raw Contact - ID: $rawContactId, Contact ID: $contactId, Account Type: '$accountType', Account Name: '$accountName'")

                if (accountType in simAccountTypes) {
                    Log.w("ContactDebug", "*** SIM CONTACT DETECTED *** - Account Type: '$accountType' - This should be filtered out!")
                } else if (accountType == null) {
                    Log.d("ContactDebug", "Device contact (NULL account type)")
                    hasNonSimContact = true
                } else {
                    Log.d("ContactDebug", "Other account type: $accountType (likely device contact)")
                    hasNonSimContact = true
                }
            }
        }

        // Create dynamic filter for all known SIM account types
        val simAccountTypesPlaceholders = simAccountTypes.joinToString(",") { "?" }
        val selectionArgs = simAccountTypes.toTypedArray()

        val finalResult = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($contactIdsList) AND " +
                "(${ContactsContract.RawContacts.ACCOUNT_TYPE} NOT IN ($simAccountTypesPlaceholders) OR ${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL)",
            selectionArgs,
            null
        )?.use { cursor ->
            val count = cursor.count
            Log.d("ContactDebug", "Filtered query (excluding all SIM types) returned $count results")

            if (count > 0) {
                Log.d("ContactDebug", "Contact is saved to device (non-SIM), returning true")
                // Let's also log which raw contacts passed the filter
                while (cursor.moveToNext()) {
                    val rawContactId = cursor.getString(0)
                    val accountType = cursor.getString(1)
                    Log.d("ContactDebug", "Device raw contact ID: $rawContactId, Account Type: '$accountType'")
                }
                cursor.moveToFirst() // Reset cursor position
            } else {
                Log.d("ContactDebug", "No device contacts found (all are SIM), returning false")
            }

            return count > 0
        } ?: run {
            Log.e("ContactDebug", "Final query failed, returning false")
            false
        }

        Log.d("ContactDebug", "Reached end of function, returning $finalResult")
        return finalResult
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        return phoneNumber.replace(Regex("[^\\d+]"), "")
    }
}
