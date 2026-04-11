package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import com.sayph.android.commons.SayphBlocker
import com.simplemobiletools.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // Block entry if the device is unregistered or in a downtime routine.
        if (SayphBlocker.checkAndBlock(this)) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
