package com.simplemobiletools.smsmessenger.helpers

import android.provider.Telephony
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Message

/**
 * Provides demo data for video demonstrations.
 * Uses UK Ofcom TV-safe phone numbers (07700 900xxx).
 */
object DemoDataProvider {

    // In-memory storage for messages sent during demo session
    private val sessionMessages = mutableMapOf<Long, MutableList<Message>>()
    private var nextSessionMessageId = 900000L

    data class DemoContact(
        val name: String,
        val phone: String,
        val threadId: Long
    )

    val contacts = listOf(
        DemoContact("Mum", "07700 900001", 1001),
        DemoContact("Dad", "07700 900002", 1002),
        DemoContact("Grandma", "07700 900003", 1003),
        DemoContact("Grandpa", "07700 900004", 1004),
        DemoContact("Granny", "07700 900005", 1005),
        DemoContact("Granddad", "07700 900006", 1006),
        DemoContact("Milo", "07700 900007", 1007),
        DemoContact("Daisy", "07700 900008", 1008),
        DemoContact("Henry", "07700 900009", 1009),
        DemoContact("Isla", "07700 900010", 1010)
    )

    fun getConversations(): ArrayList<Conversation> {
        val now = System.currentTimeMillis() / 1000
        val dayInSeconds = 86400

        return arrayListOf(
            // Mum - Yesterday 8:30am (most recent)
            createConversation(1001, "Mum", "07700 900001",
                "Good morning sweetheart! Have a lovely day at school!",
                (now - dayInSeconds + 30600).toInt()),

            // Dad - 2 days ago
            createConversation(1002, "Dad", "07700 900002",
                "If you're good!",
                (now - 2 * dayInSeconds).toInt()),

            // Milo - 3 days ago
            createConversation(1007, "Milo", "07700 900007",
                "See you at school tomorrow",
                (now - 3 * dayInSeconds).toInt()),

            // Daisy - 4 days ago
            createConversation(1008, "Daisy", "07700 900008",
                "See you!",
                (now - 4 * dayInSeconds).toInt()),

            // Isla - 5 days ago
            createConversation(1010, "Isla", "07700 900010",
                "Thank you!!",
                (now - 5 * dayInSeconds).toInt()),

            // Grandma - 1 week ago
            createConversation(1003, "Grandma", "07700 900003",
                "I had so much fun! Love you Grandma",
                (now - 7 * dayInSeconds).toInt()),

            // Henry - 1 week ago
            createConversation(1009, "Henry", "07700 900009",
                "I'll ask Mum if we can!",
                (now - 7 * dayInSeconds).toInt()),

            // Grandpa - 2 weeks ago
            createConversation(1004, "Grandpa", "07700 900004",
                "Yay! Love you Grandpa",
                (now - 14 * dayInSeconds).toInt()),

            // Granny - 2 weeks ago
            createConversation(1005, "Granny", "07700 900005",
                "Thank you Granny! Can't wait",
                (now - 14 * dayInSeconds).toInt()),

            // Granddad - 3 weeks ago
            createConversation(1006, "Granddad", "07700 900006",
                "Yes! See you soon Granddad",
                (now - 21 * dayInSeconds).toInt())
        )
    }

    fun getConversation(threadId: Long): Conversation? {
        return getConversations().find { it.threadId == threadId }
    }

    fun getMessages(threadId: Long): ArrayList<Message> {
        val demoMessages = createDemoMessages()[threadId] ?: emptyList()
        val sessionMsgs = sessionMessages[threadId] ?: emptyList()

        val allMessages = ArrayList<Message>(demoMessages)
        allMessages.addAll(sessionMsgs)
        allMessages.sortBy { it.date }

        return allMessages
    }

    fun addSessionMessage(threadId: Long, body: String) {
        val contact = contacts.find { it.threadId == threadId } ?: return

        val phoneNumber = PhoneNumber(contact.phone, 0, "", contact.phone)
        val participant = SimpleContact(
            contact.threadId.toInt(),
            contact.threadId.toInt(),
            contact.name,
            "",
            arrayListOf(phoneNumber),
            ArrayList(),
            ArrayList()
        )

        val message = Message(
            id = nextSessionMessageId++,
            body = body,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            status = Telephony.Sms.STATUS_COMPLETE,
            participants = arrayListOf(participant),
            date = (System.currentTimeMillis() / 1000).toInt(),
            read = true,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = "",
            senderName = "",
            senderPhotoUri = "",
            subscriptionId = 0,
            isScheduled = false
        )

        sessionMessages.getOrPut(threadId) { mutableListOf() }.add(message)
    }

    fun clearSessionData() {
        sessionMessages.clear()
        nextSessionMessageId = 900000L
    }

    private fun createConversation(
        threadId: Long,
        title: String,
        phoneNumber: String,
        snippet: String,
        date: Int
    ): Conversation {
        return Conversation(
            threadId = threadId,
            snippet = snippet,
            date = date,
            read = true,
            title = title,
            photoUri = "",
            isGroupConversation = false,
            phoneNumber = phoneNumber,
            isScheduled = false,
            usesCustomTitle = false,
            isArchived = false
        )
    }

    private fun createDemoMessages(): Map<Long, List<Message>> {
        val now = System.currentTimeMillis() / 1000
        val dayInSeconds = 86400L
        val hourInSeconds = 3600L
        val minuteInSeconds = 60L

        // Calculate today's midnight to use as a base for realistic times
        val todayMidnight = (now / dayInSeconds) * dayInSeconds

        return mapOf(
            // Mum (threadId: 1001) - Most recent conversation with extended history
            1001L to listOf(
                // 6 days ago - Weekend morning chat
                createMessage(1001001, 1001, "Mum", "07700 900001",
                    "Morning! Do you want pancakes for breakfast?",
                    (todayMidnight - 6 * dayInSeconds + 9 * hourInSeconds + 14 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001002, 1001, "Mum", "07700 900001",
                    "Yes please!!",
                    (todayMidnight - 6 * dayInSeconds + 9 * hourInSeconds + 16 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001003, 1001, "Mum", "07700 900001",
                    "Coming right up!",
                    (todayMidnight - 6 * dayInSeconds + 9 * hourInSeconds + 17 * minuteInSeconds).toInt(), incoming = true),

                // 5 days ago - Sunday afternoon
                createMessage(1001004, 1001, "Mum", "07700 900001",
                    "We're leaving for Grandma's in 10 minutes, are you ready?",
                    (todayMidnight - 5 * dayInSeconds + 14 * hourInSeconds + 23 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001005, 1001, "Mum", "07700 900001",
                    "Nearly! Just finding my shoes",
                    (todayMidnight - 5 * dayInSeconds + 14 * hourInSeconds + 26 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001006, 1001, "Mum", "07700 900001",
                    "They're by the front door",
                    (todayMidnight - 5 * dayInSeconds + 14 * hourInSeconds + 27 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001007, 1001, "Mum", "07700 900001",
                    "Found them!",
                    (todayMidnight - 5 * dayInSeconds + 14 * hourInSeconds + 29 * minuteInSeconds).toInt(), incoming = false),

                // 4 days ago - After school
                createMessage(1001008, 1001, "Mum", "07700 900001",
                    "I'm outside school now",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 22 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001009, 1001, "Mum", "07700 900001",
                    "Ok coming out now!",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 24 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001010, 1001, "Mum", "07700 900001",
                    "How was your day?",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 31 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001011, 1001, "Mum", "07700 900001",
                    "Really good! We had a spelling test and I got 9 out of 10",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 33 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001012, 1001, "Mum", "07700 900001",
                    "That's brilliant! Well done! What word did you get wrong?",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 34 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001013, 1001, "Mum", "07700 900001",
                    "Beautiful. I forgot the a",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 36 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001014, 1001, "Mum", "07700 900001",
                    "That's a tricky one! You'll get it next time",
                    (todayMidnight - 4 * dayInSeconds + 15 * hourInSeconds + 37 * minuteInSeconds).toInt(), incoming = true),

                // 3 days ago - Evening reminder
                createMessage(1001015, 1001, "Mum", "07700 900001",
                    "Don't forget your PE kit tomorrow!",
                    (todayMidnight - 3 * dayInSeconds + 18 * hourInSeconds + 47 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001016, 1001, "Mum", "07700 900001",
                    "Ok thanks Mum!",
                    (todayMidnight - 3 * dayInSeconds + 18 * hourInSeconds + 52 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001017, 1001, "Mum", "07700 900001",
                    "It's in the wash so I'll put it in your bag in the morning",
                    (todayMidnight - 3 * dayInSeconds + 18 * hourInSeconds + 53 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001018, 1001, "Mum", "07700 900001",
                    "Thanks Mum you're the best!",
                    (todayMidnight - 3 * dayInSeconds + 18 * hourInSeconds + 54 * minuteInSeconds).toInt(), incoming = false),

                // 2 days ago - After school conversation about art
                createMessage(1001019, 1001, "Mum", "07700 900001",
                    "How was school today?",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 8 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001020, 1001, "Mum", "07700 900001",
                    "It was good! We did art",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 12 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001021, 1001, "Mum", "07700 900001",
                    "That sounds fun! What did you make?",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 13 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001022, 1001, "Mum", "07700 900001",
                    "A painting of our cat!",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 17 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001023, 1001, "Mum", "07700 900001",
                    "Aww I can't wait to see it! Is it dry yet?",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 18 * minuteInSeconds).toInt(), incoming = true),
                createMessage(1001024, 1001, "Mum", "07700 900001",
                    "No we have to wait until Friday to bring it home",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 21 * minuteInSeconds).toInt(), incoming = false),
                createMessage(1001025, 1001, "Mum", "07700 900001",
                    "I'll make sure we put it on the fridge!",
                    (todayMidnight - 2 * dayInSeconds + 16 * hourInSeconds + 22 * minuteInSeconds).toInt(), incoming = true),

                // Yesterday morning - good morning message
                createMessage(1001026, 1001, "Mum", "07700 900001",
                    "Good morning sweetheart! Have a lovely day at school!",
                    (todayMidnight - dayInSeconds + 8 * hourInSeconds + 34 * minuteInSeconds).toInt(), incoming = true)
            ),

            // Dad (threadId: 1002) - 2 days ago
            1002L to listOf(
                createMessage(1002001, 1002, "Dad", "07700 900002",
                    "Did you finish your homework?",
                    (now - 7 * dayInSeconds).toInt(), incoming = true),
                createMessage(1002002, 1002, "Dad", "07700 900002",
                    "Yes Dad!",
                    (now - 7 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1002003, 1002, "Dad", "07700 900002",
                    "Good! Proud of you",
                    (now - 7 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1002004, 1002, "Dad", "07700 900002",
                    "Shall we go to the park on Saturday?",
                    (now - 3 * dayInSeconds).toInt(), incoming = true),
                createMessage(1002005, 1002, "Dad", "07700 900002",
                    "Yes please!",
                    (now - 2 * dayInSeconds).toInt(), incoming = false),
                createMessage(1002006, 1002, "Dad", "07700 900002",
                    "Great! We can bring the football",
                    (now - 2 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1002007, 1002, "Dad", "07700 900002",
                    "Yay! Can we get ice cream too?",
                    (now - 2 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = false),
                createMessage(1002008, 1002, "Dad", "07700 900002",
                    "If you're good!",
                    (now - 2 * dayInSeconds + 3 * hourInSeconds).toInt(), incoming = true)
            ),

            // Milo (threadId: 1007) - 3 days ago
            1007L to listOf(
                createMessage(1007001, 1007, "Milo", "07700 900007",
                    "Do you want to play at my house?",
                    (now - 14 * dayInSeconds).toInt(), incoming = true),
                createMessage(1007002, 1007, "Milo", "07700 900007",
                    "Yes! I need to ask Mum first",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1007003, 1007, "Milo", "07700 900007",
                    "Ok let me know!",
                    (now - 14 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1007004, 1007, "Milo", "07700 900007",
                    "She said yes! See you Saturday",
                    (now - 14 * dayInSeconds + 3 * hourInSeconds).toInt(), incoming = false),
                createMessage(1007005, 1007, "Milo", "07700 900007",
                    "That was so fun!",
                    (now - 4 * dayInSeconds).toInt(), incoming = false),
                createMessage(1007006, 1007, "Milo", "07700 900007",
                    "I know! Let's do it again",
                    (now - 3 * dayInSeconds).toInt(), incoming = true),
                createMessage(1007007, 1007, "Milo", "07700 900007",
                    "Definitely!",
                    (now - 3 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1007008, 1007, "Milo", "07700 900007",
                    "See you at school tomorrow",
                    (now - 3 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true)
            ),

            // Daisy (threadId: 1008) - 4 days ago
            1008L to listOf(
                createMessage(1008001, 1008, "Daisy", "07700 900008",
                    "Did you see the new puppy?",
                    (now - 14 * dayInSeconds).toInt(), incoming = false),
                createMessage(1008002, 1008, "Daisy", "07700 900008",
                    "No! Send me a picture!",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1008003, 1008, "Daisy", "07700 900008",
                    "He's called Biscuit",
                    (now - 14 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = false),
                createMessage(1008004, 1008, "Daisy", "07700 900008",
                    "So cute!! I want to meet him",
                    (now - 14 * dayInSeconds + 3 * hourInSeconds).toInt(), incoming = true),
                createMessage(1008005, 1008, "Daisy", "07700 900008",
                    "Are you coming to swimming on Thursday?",
                    (now - 5 * dayInSeconds).toInt(), incoming = true),
                createMessage(1008006, 1008, "Daisy", "07700 900008",
                    "Yes! Are you?",
                    (now - 4 * dayInSeconds).toInt(), incoming = false),
                createMessage(1008007, 1008, "Daisy", "07700 900008",
                    "Yes! See you there",
                    (now - 4 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1008008, 1008, "Daisy", "07700 900008",
                    "See you!",
                    (now - 4 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = false)
            ),

            // Isla (threadId: 1010) - 5 days ago
            1010L to listOf(
                createMessage(1010001, 1010, "Isla", "07700 900010",
                    "What did you get for your birthday?",
                    (now - 21 * dayInSeconds).toInt(), incoming = true),
                createMessage(1010002, 1010, "Isla", "07700 900010",
                    "A new bike! It's blue",
                    (now - 21 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1010003, 1010, "Isla", "07700 900010",
                    "Lucky! I love blue",
                    (now - 21 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1010004, 1010, "Isla", "07700 900010",
                    "Want to come to the park tomorrow?",
                    (now - 7 * dayInSeconds).toInt(), incoming = false),
                createMessage(1010005, 1010, "Isla", "07700 900010",
                    "I can't, we're visiting my nan",
                    (now - 7 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1010006, 1010, "Isla", "07700 900010",
                    "Happy birthday Isla!!",
                    (now - 5 * dayInSeconds).toInt(), incoming = false),
                createMessage(1010007, 1010, "Isla", "07700 900010",
                    "Thank you!!",
                    (now - 5 * dayInSeconds + hourInSeconds).toInt(), incoming = true)
            ),

            // Grandma (threadId: 1003) - 1 week ago
            1003L to listOf(
                createMessage(1003001, 1003, "Grandma", "07700 900003",
                    "Hello my darling! Miss you lots",
                    (now - 21 * dayInSeconds).toInt(), incoming = true),
                createMessage(1003002, 1003, "Grandma", "07700 900003",
                    "Miss you too Grandma!",
                    (now - 21 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1003003, 1003, "Grandma", "07700 900003",
                    "Maybe you can visit soon?",
                    (now - 21 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1003004, 1003, "Grandma", "07700 900003",
                    "Mum said we can come on Sunday!",
                    (now - 14 * dayInSeconds).toInt(), incoming = false),
                createMessage(1003005, 1003, "Grandma", "07700 900003",
                    "Wonderful! I'll bake your favourite cake",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1003006, 1003, "Grandma", "07700 900003",
                    "It was lovely to see you! Come again soon",
                    (now - 7 * dayInSeconds).toInt(), incoming = true),
                createMessage(1003007, 1003, "Grandma", "07700 900003",
                    "I had so much fun! Love you Grandma",
                    (now - 7 * dayInSeconds + hourInSeconds).toInt(), incoming = false)
            ),

            // Henry (threadId: 1009) - 1 week ago
            1009L to listOf(
                createMessage(1009001, 1009, "Henry", "07700 900009",
                    "Did you finish the book?",
                    (now - 21 * dayInSeconds).toInt(), incoming = false),
                createMessage(1009002, 1009, "Henry", "07700 900009",
                    "Yes! It was so good",
                    (now - 21 * dayInSeconds + hourInSeconds).toInt(), incoming = true),
                createMessage(1009003, 1009, "Henry", "07700 900009",
                    "I know! Best book ever",
                    (now - 21 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = false),
                createMessage(1009004, 1009, "Henry", "07700 900009",
                    "Have you seen the new dinosaur film?",
                    (now - 14 * dayInSeconds).toInt(), incoming = true),
                createMessage(1009005, 1009, "Henry", "07700 900009",
                    "Not yet! Is it good?",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1009006, 1009, "Henry", "07700 900009",
                    "Yes! You have to watch it",
                    (now - 7 * dayInSeconds).toInt(), incoming = true),
                createMessage(1009007, 1009, "Henry", "07700 900009",
                    "I'll ask Mum if we can!",
                    (now - 7 * dayInSeconds + hourInSeconds).toInt(), incoming = false)
            ),

            // Grandpa (threadId: 1004) - 2 weeks ago
            1004L to listOf(
                createMessage(1004001, 1004, "Grandpa", "07700 900004",
                    "Hello! How are you doing at school?",
                    (now - 30 * dayInSeconds).toInt(), incoming = true),
                createMessage(1004002, 1004, "Grandpa", "07700 900004",
                    "Good! I got a gold star",
                    (now - 30 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1004003, 1004, "Grandpa", "07700 900004",
                    "Well done! So proud of you",
                    (now - 30 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1004004, 1004, "Grandpa", "07700 900004",
                    "Can we go to the ducks next time?",
                    (now - 21 * dayInSeconds).toInt(), incoming = false),
                createMessage(1004005, 1004, "Grandpa", "07700 900004",
                    "Of course! I'll bring bread for them",
                    (now - 14 * dayInSeconds).toInt(), incoming = true),
                createMessage(1004006, 1004, "Grandpa", "07700 900004",
                    "Yay! Love you Grandpa",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = false)
            ),

            // Granny (threadId: 1005) - 2 weeks ago
            1005L to listOf(
                createMessage(1005001, 1005, "Granny", "07700 900005",
                    "Did you like the jumper I knitted you?",
                    (now - 30 * dayInSeconds).toInt(), incoming = true),
                createMessage(1005002, 1005, "Granny", "07700 900005",
                    "Yes! It's so cosy",
                    (now - 30 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1005003, 1005, "Granny", "07700 900005",
                    "I'm so glad! Blue is your colour",
                    (now - 30 * dayInSeconds + 2 * hourInSeconds).toInt(), incoming = true),
                createMessage(1005004, 1005, "Granny", "07700 900005",
                    "Granny can you teach me to knit?",
                    (now - 21 * dayInSeconds).toInt(), incoming = false),
                createMessage(1005005, 1005, "Granny", "07700 900005",
                    "Of course my love! Next time you visit",
                    (now - 14 * dayInSeconds).toInt(), incoming = true),
                createMessage(1005006, 1005, "Granny", "07700 900005",
                    "Thank you Granny! Can't wait",
                    (now - 14 * dayInSeconds + hourInSeconds).toInt(), incoming = false)
            ),

            // Granddad (threadId: 1006) - 3 weeks ago
            1006L to listOf(
                createMessage(1006001, 1006, "Granddad", "07700 900006",
                    "How is school going?",
                    (now - 60 * dayInSeconds).toInt(), incoming = true),
                createMessage(1006002, 1006, "Granddad", "07700 900006",
                    "Good! I made a new friend",
                    (now - 60 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1006003, 1006, "Granddad", "07700 900006",
                    "That's wonderful! What's their name?",
                    (now - 30 * dayInSeconds).toInt(), incoming = true),
                createMessage(1006004, 1006, "Granddad", "07700 900006",
                    "Milo! He's really nice",
                    (now - 30 * dayInSeconds + hourInSeconds).toInt(), incoming = false),
                createMessage(1006005, 1006, "Granddad", "07700 900006",
                    "I'm glad. Making friends is so important",
                    (now - 21 * dayInSeconds).toInt(), incoming = true),
                createMessage(1006006, 1006, "Granddad", "07700 900006",
                    "Yes! See you soon Granddad",
                    (now - 21 * dayInSeconds + hourInSeconds).toInt(), incoming = false)
            )
        )
    }

    private fun createMessage(
        id: Long,
        threadId: Long,
        contactName: String,
        contactPhone: String,
        body: String,
        date: Int,
        incoming: Boolean
    ): Message {
        val phoneNumber = PhoneNumber(contactPhone, 0, "", contactPhone)
        val participant = SimpleContact(
            threadId.toInt(),
            threadId.toInt(),
            contactName,
            "",
            arrayListOf(phoneNumber),
            ArrayList(),
            ArrayList()
        )

        return Message(
            id = id,
            body = body,
            type = if (incoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
            status = Telephony.Sms.STATUS_COMPLETE,
            participants = arrayListOf(participant),
            date = date,
            read = true,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = if (incoming) contactPhone else "",
            senderName = if (incoming) contactName else "",
            senderPhotoUri = "",
            subscriptionId = 0,
            isScheduled = false
        )
    }
}
