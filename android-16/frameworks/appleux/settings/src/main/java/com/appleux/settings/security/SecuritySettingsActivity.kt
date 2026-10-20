package com.appleux.settings.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Toast
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R
import com.appleux.settings.detail.SettingsDetailActivity
import com.appleux.design.components.AppleuxToast

/**
 * Owned Security & Privacy page.
 *
 * Replaces a stub that forwarded both rows to `ACTION_SECURITY_SETTINGS`
 * (stock). Face ID maps onto the platform's own biometric enrolment, which is
 * the real surface behind the iOS row; Data Protection reports the actual
 * device lock state instead of linking away.
 */
class SecuritySettingsActivity : SettingsDetailActivity() {

    private lateinit var card: AppleuxCard
    private lateinit var list: android.widget.LinearLayout

    override val titleText: String get() = getString(R.string.security)

    override fun onContentReady() {
        list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        card = AppleuxCard(this).apply {
            applyLiquidGlassRim()
            addView(list)
        }
        content.addView(
            ScrollView(this).apply { addView(card) },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()

        list.addView(
            AppleuxListItem(this).apply {
                setTitle(getString(R.string.security_face_id))
                setSubtitle(biometricCapability())
                setDividerVisible(true)
                setOnClickListener { openBiometricEnrolment() }
            }
        )
        list.addView(
            AppleuxListItem(this).apply {
                setTitle(getString(R.string.security_encryption))
                setSubtitle(
                    getString(
                        if (isDeviceSecure()) R.string.data_protection_on
                        else R.string.data_protection_off
                    )
                )
                setDividerVisible(false)
            }
        )
    }

    /**
     * iOS labels the row "Face ID" when the hardware is there and shows the
     * set-up state otherwise. We report hardware presence, because enrolment
     * state lives behind an activity this image may not ship.
     */
    private fun biometricCapability(): String = getString(
        if (canEnrolBiometrics()) R.string.security_face_id
        else R.string.security_face_id_unavailable
    )

    /**
     * Hardware presence, not enrolment state: BiometricManager.from() is API 29+
     * and absent from this build's android.jar, so the probe asks PackageManager
     * whether a biometric sensor exists at all (API 23+). That is the fact the
     * row actually needs — whether enrolment can be offered here.
     */
    private fun canEnrolBiometrics(): Boolean = runCatching {
        packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
    }.getOrDefault(false)

    private fun isDeviceSecure(): Boolean = runCatching {
        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isDeviceSecure == true
    }.getOrDefault(false)

    private fun openBiometricEnrolment() {
        // ACTION_BIOMETRIC_ENROLL is API 30+; the emulator image is 36 but the
        // app builds at minSdk 26, so the constant is reached by name.
        val action = "android.settings.BIOMETRIC_ENROLL"
        runCatching {
            startActivity(Intent(action))
        }.onFailure { reportEnrolUnavailable() }
    }

    /** Say plainly that enrolment is unavailable; never silent-fail. */
    private fun reportEnrolUnavailable() {
        AppleuxToast.show(this, getString(R.string.biometric_enroll_unavailable))
    }
}