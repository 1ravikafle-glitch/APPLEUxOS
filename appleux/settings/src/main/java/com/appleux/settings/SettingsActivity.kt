package com.appleux.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.theme.AppleuxTheme
import com.appleux.settings.ui.SettingsListActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppleuxTheme.applyToActivity(this)
        startActivity(Intent(this, SettingsListActivity::class.java))
        finish()
    }
}