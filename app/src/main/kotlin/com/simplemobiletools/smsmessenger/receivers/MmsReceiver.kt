package com.simplemobiletools.smsmessenger.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.extensions.normalizePhoneNumber
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.getConversations
import com.simplemobiletools.smsmessenger.extensions.getLatestMMS
import com.simplemobiletools.smsmessenger.extensions.insertOrUpdateConversation
import com.simplemobiletools.smsmessenger.extensions.showReceivedMessageNotification
import com.simplemobiletools.smsmessenger.extensions.updateUnreadCountBadge
import com.simplemobiletools.smsmessenger.helpers.MessageSyncHelper
import com.simplemobiletools.smsmessenger.helpers.MmsPermissionHelper
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()

        // Check if number is blocked in the standard way
        if (context.isNumberBlocked(normalizedAddress)) {
            return true
        }

        // Check if MMS is allowed for this contact via SayphAgent
        // Return true (blocked) if MMS is not allowed
        return !MmsPermissionHelper.isMmsAllowedForContact(context, normalizedAddress)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        // Check if MMS is allowed for this contact
        if (!MmsPermissionHelper.isMmsAllowedForContact(context, address)) {
            // Silently block the MMS - do not show notification or store in database
            return
        }

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            val glideBitmap = try {
                Glide.with(context)
                    .asBitmap()
                    .load(mms.attachment!!.attachments.first().getUri())
                    .centerCrop()
                    .into(size, size)
                    .get()
            } catch (e: Exception) {
                null
            }

            // Log the inbound MMS for sync
            MessageSyncHelper.logMessage(
                context = context,
                address = address,
                body = mms.body,
                direction = "inbound",
                msgType = "mms",
                timestamp = System.currentTimeMillis()
            )

            Handler(Looper.getMainLooper()).post {
                context.showReceivedMessageNotification(mms.id, address, mms.body, mms.threadId, glideBitmap)
                val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return@post
                ensureBackgroundThread {
                    context.insertOrUpdateConversation(conversation)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    refreshMessages()
                }
            }
        }
    }

    override fun onError(context: Context, error: String) = context.showErrorToast(context.getString(R.string.couldnt_download_mms))
}
