package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.SimpleContact

/**
 * Helper class that loads contacts directly from ContactsContract,
 * excluding SIM contacts. This bypasses SimpleContactsHelper which
 * deduplicates by phone number and can hide device contacts when
 * a SIM contact has the same number.
 */
class DeviceContactsHelper(private val context: Context) {

    companion object {
        private const val TAG = "DeviceContactsHelper"

        // SIM account types to exclude
        private val SIM_ACCOUNT_TYPES = setOf(
            "com.android.contacts.sim",
            "vnd.sec.contact.sim",
            "com.android.sim",
            "sim",
            "SIM"
        )
    }

    /**
     * Get all device contacts (excluding SIM contacts).
     * Returns contacts with phone numbers only.
     */
    fun getDeviceContacts(callback: (ArrayList<SimpleContact>) -> Unit) {
        Thread {
            val contacts = loadDeviceContacts()
            callback(contacts)
        }.start()
    }

    /**
     * Get all device contacts synchronously.
     */
    fun getDeviceContactsSync(): ArrayList<SimpleContact> {
        return loadDeviceContacts()
    }

    private fun loadDeviceContacts(): ArrayList<SimpleContact> {
        val contacts = ArrayList<SimpleContact>()
        val contactsMap = mutableMapOf<Long, ContactBuilder>()

        // Build the exclusion clause for SIM account types
        val simTypesPlaceholders = SIM_ACCOUNT_TYPES.joinToString(",") { "?" }
        val simTypesArgs = SIM_ACCOUNT_TYPES.toTypedArray()

        // First, get all raw contacts that are NOT SIM contacts
        val rawContactIds = mutableSetOf<Long>()

        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            ),
            "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} NOT IN ($simTypesPlaceholders)) AND " +
                "${ContactsContract.RawContacts.ACCOUNT_NAME} NOT LIKE '%sim%'",
            simTypesArgs,
            null
        )?.use { cursor ->
            val rawIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val contactIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)

            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(rawIdIdx)
                val contactId = cursor.getLong(contactIdIdx)
                val accountType = cursor.getString(accountTypeIdx)
                val accountName = cursor.getString(accountNameIdx)

                // Double-check: exclude if account name contains "sim"
                if (accountName?.contains("sim", ignoreCase = true) == true) {
                    Log.d(TAG, "Excluding contact with SIM account name: $accountName")
                    continue
                }

                rawContactIds.add(rawId)
                if (!contactsMap.containsKey(rawId)) {
                    contactsMap[rawId] = ContactBuilder(rawId.toInt(), contactId.toInt())
                }
            }
        }

        if (rawContactIds.isEmpty()) {
            Log.d(TAG, "No device contacts found")
            return contacts
        }

        Log.d(TAG, "Found ${rawContactIds.size} device raw contacts")

        // Now get contact data (names and phone numbers) for these raw contacts
        val rawIdsString = rawContactIds.joinToString(",")

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} IN ($rawIdsString)",
            null,
            null
        )?.use { cursor ->
            val rawContactIdIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
            val mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)

            while (cursor.moveToNext()) {
                val rawContactId = cursor.getLong(rawContactIdIdx)
                val mimeType = cursor.getString(mimeTypeIdx)
                val data1 = cursor.getString(data1Idx) ?: continue

                val builder = contactsMap[rawContactId] ?: continue

                when (mimeType) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        builder.name = data1
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val phoneType = cursor.getInt(data2Idx)
                        val normalizedNumber = data1.replace(Regex("[^\\d+]"), "")
                        builder.phoneNumbers.add(PhoneNumber(data1, phoneType, "", normalizedNumber))
                    }
                }
            }
        }

        // Get photo URIs
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val photoUriIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                val photoUri = cursor.getString(photoUriIdx) ?: ""

                // Find builders with this contact ID and set photo
                contactsMap.values
                    .filter { it.contactId.toLong() == contactId }
                    .forEach { it.photoUri = photoUri }
            }
        }

        // Build SimpleContact objects
        for (builder in contactsMap.values) {
            // Only include contacts with phone numbers
            if (builder.phoneNumbers.isNotEmpty() && builder.name.isNotEmpty()) {
                val contact = SimpleContact(
                    builder.rawId,
                    builder.contactId,
                    builder.name,
                    builder.photoUri,
                    builder.phoneNumbers,
                    ArrayList(),
                    ArrayList()
                )
                contacts.add(contact)
            }
        }

        // Sort by name
        contacts.sortBy { it.name.lowercase() }

        Log.d(TAG, "Returning ${contacts.size} device contacts with phone numbers")
        return contacts
    }

    /**
     * Check if a phone number belongs to a device contact (not SIM).
     */
    fun isDeviceContact(phoneNumber: String): Boolean {
        if (phoneNumber.isEmpty()) return false

        val normalizedNumber = phoneNumber.replace(Regex("[^\\d+]"), "")

        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalizedNumber)
            .build()

        val contactIds = mutableSetOf<Long>()

        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                contactIds.add(cursor.getLong(0))
            }
        }

        if (contactIds.isEmpty()) return false

        // Check if any of these contacts are device contacts (not SIM)
        val simTypesPlaceholders = SIM_ACCOUNT_TYPES.joinToString(",") { "?" }
        val contactIdsString = contactIds.joinToString(",")

        return context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($contactIdsString) AND " +
                "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} NOT IN ($simTypesPlaceholders))",
            SIM_ACCOUNT_TYPES.toTypedArray(),
            null
        )?.use { cursor ->
            cursor.count > 0
        } ?: false
    }

    /**
     * Get contact name for a phone number (device contacts only).
     */
    fun getContactName(phoneNumber: String): String? {
        if (phoneNumber.isEmpty()) return null

        val normalizedNumber = phoneNumber.replace(Regex("[^\\d+]"), "")

        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalizedNumber)
            .build()

        context.contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val displayName = cursor.getString(1)

                // Check if this contact is a device contact
                if (isContactIdDeviceContact(contactId)) {
                    return displayName
                }
            }
        }

        return null
    }

    private fun isContactIdDeviceContact(contactId: Long): Boolean {
        val simTypesPlaceholders = SIM_ACCOUNT_TYPES.joinToString(",") { "?" }

        return context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} NOT IN ($simTypesPlaceholders))",
            arrayOf(contactId.toString()) + SIM_ACCOUNT_TYPES.toTypedArray(),
            null
        )?.use { cursor ->
            cursor.count > 0
        } ?: false
    }

    private class ContactBuilder(
        val rawId: Int,
        val contactId: Int,
        var name: String = "",
        var photoUri: String = "",
        val phoneNumbers: ArrayList<PhoneNumber> = ArrayList()
    )
}
