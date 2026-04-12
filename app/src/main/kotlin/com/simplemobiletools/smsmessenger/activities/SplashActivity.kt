package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // Downtime/unregistered blocking is enforced process-wide by SayphActivityGuard,
        // which is installed from App.onCreate and intercepts every activity start.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
