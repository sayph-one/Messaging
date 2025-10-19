package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityNewConversationBinding
import com.simplemobiletools.smsmessenger.databinding.ItemSuggestedContactBinding
import com.simplemobiletools.smsmessenger.extensions.getSuggestedContacts
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.messaging.isShortCodeWithLetters
import android.widget.Toast
import java.net.URLDecoder
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.newConversationCoordinator,
            nestedView = binding.contactsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.contactsList, toolbar = binding.newConversationToolbar)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.newConversationToolbar, NavigationIcon.Arrow)
        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.noContactsPlaceholder2.underlineText()
        binding.suggestionsLabel.setTextColor(getProperPrimaryColor())
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        binding.newConversationAddress.onTextChangeListener { searchString ->
            val filteredContacts = ArrayList<SimpleContact>()
            allContacts.forEach { contact ->
                if (contact.phoneNumbers.any { it.normalizedNumber.contains(searchString, true) } ||
                    contact.name.contains(searchString, true) ||
                    contact.name.contains(searchString.normalizeString(), true) ||
                    contact.name.normalizeString().contains(searchString, true)) {
                    filteredContacts.add(contact)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

//            binding.newConversationConfirm.beVisibleIf(searchString.length > 2)
        }

        binding.newConversationConfirm.applyColorFilter(getProperTextColor())
        binding.newConversationConfirm.setOnClickListener {
            val number = binding.newConversationAddress.value
            val matchedContact = allContacts.firstOrNull { contact ->
                contact.phoneNumbers.any { it.normalizedNumber == number || it.value == number }
            }

            if (matchedContact != null) {
                launchThreadActivity(number, matchedContact.name)
            } else {
                toast("You can only message saved contacts.")
                binding.newConversationAddress.setText("")
            }
        }

        binding.noContactsPlaceholder2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = getProperTextColor().getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()

        binding.newConversationConfirm.apply {
            beGone()
            isEnabled = false
            isClickable = false
        }
    }

    private fun isThirdPartyIntent(): Boolean {
        if ((intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) && intent.dataString != null) {
            val number = intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").replace("+", "%2b").trim()
            launchThreadActivity(URLDecoder.decode(number), "")
            finish()
            return true
        }
        return false
    }


    private fun fetchContacts() {
        val contactFilterHelper = ContactFilterHelper(this)

        fillSuggestedContacts {
            SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
                // Filter out SIM contacts
                Log.d("ContactDebug", "Total contacts before filtering: ${contacts.size}")

                allContacts = contacts.filter { contact ->
                    // Check if any phone number for this contact is saved to device (not SIM)
                    val hasDeviceNumber = contact.phoneNumbers.any { phoneNumber ->
                        val isDeviceContact = contactFilterHelper.isContactSaved(phoneNumber.normalizedNumber)
                        Log.d("ContactDebug", "Contact: ${contact.name}, Number: ${phoneNumber.normalizedNumber}, IsDevice: $isDeviceContact")
                        isDeviceContact
                    }

                    if (!hasDeviceNumber) {
                        Log.d("ContactDebug", "Filtering out SIM contact: ${contact.name}")
                    }

                    hasDeviceNumber
                } as ArrayList<SimpleContact>

                Log.d("ContactDebug", "Total contacts after filtering: ${allContacts.size}")

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    setupAdapter(allContacts)
                }
            }
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(!hasContacts && !hasPermission(PERMISSION_READ_CONTACTS))

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) {
                com.simplemobiletools.commons.R.string.no_contacts_found
            } else {
                com.simplemobiletools.commons.R.string.no_access_to_contacts
            }

            binding.noContactsPlaceholder.text = getString(placeholderText)
        }

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                hideKeyboard()
                val contact = it as SimpleContact
                val phoneNumbers = contact.phoneNumbers
                if (phoneNumbers.size > 1) {
                    val primaryNumber = contact.phoneNumbers.find { it.isPrimary }
                    if (primaryNumber != null) {
                        launchThreadActivity(primaryNumber.value, contact.name)
                    } else {
                        val items = ArrayList<RadioItem>()
                        phoneNumbers.forEachIndexed { index, phoneNumber ->
                            val type = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                            items.add(RadioItem(index, "${phoneNumber.normalizedNumber} ($type)", phoneNumber.normalizedNumber))
                        }

                        RadioGroupDialog(this, items) {
                            launchThreadActivity(it as String, contact.name)
                        }
                    }
                } else {
                    launchThreadActivity(phoneNumbers.first().normalizedNumber, contact.name)
                }
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts)
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        val privateCursor = getMyContactsCursor(false, true)
        val contactFilterHelper = ContactFilterHelper(this)

        ensureBackgroundThread {
            val allPrivateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            // Filter out SIM contacts from private contacts too
            Log.d("ContactDebug", "Private contacts before filtering: ${allPrivateContacts.size}")

            privateContacts = allPrivateContacts.filter { contact ->
                val hasDeviceNumber = contact.phoneNumbers.any { phoneNumber ->
                    val isDeviceContact = contactFilterHelper.isContactSaved(phoneNumber.normalizedNumber)
                    Log.d("ContactDebug", "Private Contact: ${contact.name}, Number: ${phoneNumber.normalizedNumber}, IsDevice: $isDeviceContact")
                    isDeviceContact
                }

                if (!hasDeviceNumber) {
                    Log.d("ContactDebug", "Filtering out SIM private contact: ${contact.name}")
                }

                hasDeviceNumber
            } as ArrayList<SimpleContact>

            Log.d("ContactDebug", "Private contacts after filtering: ${privateContacts.size}")

            val suggestions = getSuggestedContacts(privateContacts)
            runOnUiThread {
                binding.suggestionsHolder.removeAllViews()
                if (suggestions.isEmpty()) {
                    binding.suggestionsLabel.beGone()
                    binding.suggestionsScrollview.beGone()
                } else {
                    binding.suggestionsLabel.beVisible()
                    binding.suggestionsScrollview.beVisible()
                    suggestions.forEach {
                        val contact = it
                        ItemSuggestedContactBinding.inflate(layoutInflater).apply {
                            suggestedContactName.text = contact.name
                            suggestedContactName.setTextColor(getProperTextColor())

                            if (!isDestroyed) {
                                SimpleContactsHelper(this@NewConversationActivity).loadContactImage(contact.photoUri, suggestedContactImage, contact.name)
                                binding.suggestionsHolder.addView(root)
                                root.setOnClickListener {
                                    launchThreadActivity(contact.phoneNumbers.first().normalizedNumber, contact.name)
                                }
                            }
                        }
                    }
                }
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()).normalizeString())
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String) {
        hideKeyboard()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)

        // Check if there are attachments being shared
        val hasAttachments = (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) ||
                            (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true)

        // If there are attachments, check MMS permissions
        if (hasAttachments) {
            ensureBackgroundThread {
                val phoneNumbersList = numbers.toList()
                Log.d("NewConversationActivity", "Checking MMS permissions for: $phoneNumbersList (from: $phoneNumber)")
                val isMmsAllowed = MmsPermissionHelper.isMmsAllowedForContacts(this, phoneNumbersList)
                Log.d("NewConversationActivity", "MMS permission result: $isMmsAllowed")

                runOnUiThread {
                    if (!isMmsAllowed) {
                        toast(R.string.mms_not_allowed_for_contact)
                        Log.w("NewConversationActivity", "Blocking MMS share - no permission for $phoneNumbersList")
                        finish()
                        return@runOnUiThread
                    }

                    // MMS is allowed, proceed with launching ThreadActivity
                    startThreadActivityWithIntent(numbers, number, text)
                }
            }
        } else {
            // No attachments, proceed normally
            startThreadActivityWithIntent(numbers, number, text)
        }
    }

    private fun startThreadActivityWithIntent(numbers: Set<String>, number: String, text: String) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, binding.newConversationAddress.value)
            putExtra(THREAD_TEXT, text)
            putExtra(THREAD_NUMBER, number)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}
