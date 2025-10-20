package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.simplemobiletools.commons.extensions.normalizePhoneNumber

/**
 * Helper class to check MMS permissions for contacts via the SayphAgent ContentProvider.
 * This integrates with the SayphAgent app which manages MMS permissions remotely.
 */
object MmsPermissionHelper {
    private const val TAG = "MmsPermissionHelper"
    private const val AUTHORITY = "com.sayph.sayphagent.mmspermissions"
    private const val COLUMN_PHONE_NUMBER = "phone_number"
    private const val COLUMN_MMS_ALLOWED = "mms_allowed"

    /**
     * Check if MMS is allowed for a specific phone number.
     *
     * @param context Android context
     * @param phoneNumber The phone number to check
     * @return true if MMS is allowed, false if blocked or on error (fail-safe)
     */
    fun isMmsAllowedForContact(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isEmpty()) {
            Log.w(TAG, "Empty phone number provided")
            return false
        }

        // Normalize the phone number for consistency
        val normalizedNumber = phoneNumber.normalizePhoneNumber()

        return try {
            val uri = Uri.parse("content://$AUTHORITY/check/$normalizedNumber")
            Log.d(TAG, "Checking MMS permission for: $normalizedNumber (original: $phoneNumber)")

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val allowedIndex = it.getColumnIndex(COLUMN_MMS_ALLOWED)
                    if (allowedIndex != -1) {
                        val isAllowed = it.getInt(allowedIndex) == 1
                        Log.d(TAG, "MMS permission for $normalizedNumber: $isAllowed")
                        return isAllowed
                    } else {
                        Log.w(TAG, "Column $COLUMN_MMS_ALLOWED not found in cursor")
                    }
                }
            }

            // Default to blocking if no result found
            Log.w(TAG, "No MMS permission data found for $normalizedNumber, defaulting to blocked")
            false
        } catch (e: SecurityException) {
            // SayphAgent might not have granted permission to this provider
            Log.e(TAG, "SecurityException accessing MMS permissions provider", e)
            false
        } catch (e: IllegalArgumentException) {
            // Provider might not exist (SayphAgent not installed)
            Log.e(TAG, "SayphAgent provider not found - app may not be installed", e)
            false
        } catch (e: Exception) {
            // Any other error - fail safe by blocking MMS
            Log.e(TAG, "Failed to check MMS permission for $normalizedNumber", e)
            false
        }
    }

    /**
     * Check if MMS is allowed for multiple phone numbers.
     * Returns true only if ALL numbers have MMS permission.
     *
     * @param context Android context
     * @param phoneNumbers List of phone numbers to check
     * @return true if MMS is allowed for ALL numbers, false otherwise
     */
    fun isMmsAllowedForContacts(context: Context, phoneNumbers: List<String>): Boolean {
        if (phoneNumbers.isEmpty()) {
            Log.w(TAG, "Empty phone numbers list provided")
            return false
        }

        // For group messages, all participants must have MMS permission
        return phoneNumbers.all { phoneNumber ->
            isMmsAllowedForContact(context, phoneNumber)
        }
    }

    /**
     * Check if the SayphAgent provider is available.
     * Useful for showing appropriate error messages.
     *
     * @param context Android context
     * @return true if SayphAgent is installed and provider is accessible
     */
    fun isSayphAgentAvailable(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://$AUTHORITY/check/test")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "SayphAgent provider not available: ${e.message}")
            false
        }
    }
}