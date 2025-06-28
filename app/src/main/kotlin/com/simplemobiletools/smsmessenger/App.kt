package com.simplemobiletools.smsmessenger

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.graphics.Color
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.commons.helpers.BaseConfig
import androidx.core.graphics.toColorInt

class App : Application() {
    @SuppressLint("UseKtx")
    override fun onCreate() {
        super.onCreate()

        val config = BaseConfig(this)
        config.primaryColor = "#132d4d".toColorInt()
        config.appIconColor = "#132d4d".toColorInt()

        val prefs = getSharedPreferences("BaseConfig", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("block_unknown_numbers", false)
            .putBoolean("block_hidden_numbers", false)
            .putInt("primary_color", "#132d4d".toColorInt())
            .putInt("app_icon_color", "#132d4d".toColorInt())
            .apply()


        checkUseEnglish()
    }
}
