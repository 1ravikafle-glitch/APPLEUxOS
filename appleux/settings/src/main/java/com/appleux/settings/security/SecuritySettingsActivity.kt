package com.appleux.settings.security

import com.appleux.settings.R
import android.content.Context
import android.content.Intent
import android.provider.Settings

class SecuritySettingsActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.security_settings)
    }

    private fun openBiometricSettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        startActivity(intent)
    }

    private fun openEncryptionSettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        startActivity(intent)
    }
}
