package com.appleux.settings.display

import com.appleux.settings.R
import android.content.Context
import android.content.Intent
import android.provider.Settings

class DisplaySettingsActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.display_settings)
    }

    private fun openBrightnessSettings() {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        startActivity(intent)
    }
}
