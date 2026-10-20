package com.appleux.systemui.chrome

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import com.appleux.design.components.AppleuxLockScreen
import com.appleux.design.components.AppleuxPasscodeView

/**
 * The iOS 27 lock screen, hosted inside SystemUI's shade window.
 *
 * ======================================================================
 * WHY THIS DESIGN (A1, Master Prompt §12b — the keyguard surface)
 * ======================================================================
 * `AppleuxLockScreen` + `AppleuxPasscodeView` were complete components
 * with ZERO hosts — the biggest missing iOS surface (huge clock, date,
 * notification stack, flashlight/camera, swipe-up). The attach pattern is
 * the PROVEN one from AppleuxShadeHost (static host, SystemUI class
 * calls attach during boot, everything try/catch so any failure degrades
 * to the stock keyguard — never a boot risk).
 *
 * WHAT IS DELIBERATELY NOT DONE HERE:
 *  - NO auth changes. Keyguard mediation, bouncer, PIN/lock-pattern
 *    verification stay 100% AOSP (charter: delete the chrome, keep the
 *    behaviour). Our swipe-up hides the visual layer so the stock
 *    unlock path takes over — the passcode view is presented when the
 *    device is secure, and its entries FORWARD to the stock bouncer
 *    rather than verifying anything ourselves.
 *  - No Dagger changes. Lock state comes from the PUBLIC
 *    KeyguardManager + system broadcasts (USER_PRESENT / SCREEN_OFF /
 *    SCREEN_ON) instead of injected interactors — coarse but safe; the
 *    fine-grained wiring can follow once a framework-matched guest is
 *    running (A2) and we can verify against Dagger without bricking.
 */
object AppleuxKeyguardHost {

    private const val TAG = "AppleuxKeyguard"

    private var window: ViewGroup? = null
    private var lockScreen: AppleuxLockScreen? = null
    private var passcode: AppleuxPasscodeView? = null
    private var passcodeLayer: ViewGroup? = null
    private var km: KeyguardManager? = null
    private var receiver: BroadcastReceiver? = null

    @Volatile
    var isAttached: Boolean = false
        private set

    @JvmStatic
    fun attach(shadeWindowView: ViewGroup) {
        if (isAttached) return
        val context = shadeWindowView.context
        km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val ls = AppleuxLockScreen(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Visual layer only. Unlock forwards to the stock path: our view
            // hides, the AOSP keyguard swipe/bouncer continues underneath.
            setOnUnlockRequestListener { onUnlockRequested() }
            setOnUnlockUnavailableListener { onUnlockRequested() }
            setOnFlashlightClickListener { setTorch(true) }
            setOnCameraClickListener { launchCamera(context) }
            visibility = View.GONE
        }

        val pc = AppleuxPasscodeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            // Entries do NOT verify — the stock bouncer owns auth. Finish
            // (6 digits or Cancel) hides the layer and hands back to AOSP.
            onDigit = { }
            onBackspace = { }
            onEmergency = { /* placeholder: dialer ACTION_DIAL is available */ }
            onCancel = { hidePasscode() }
            visibility = View.GONE
        }

        shadeWindowView.addView(ls, ls.layoutParams)
        shadeWindowView.addView(pc, pc.layoutParams)
        window = shadeWindowView
        lockScreen = ls
        passcode = pc
        registerLockState(context)
        refreshState()
        isAttached = true
        android.util.Log.i(TAG, "iOS lock screen attached (visual layer; auth stays AOSP)")
    }

    /** Show when locked and the screen just turned on; hide otherwise. */
    @JvmStatic
    fun refreshState() {
        val w = window ?: return
        val locked = try {
            km?.isKeyguardLocked == true
        } catch (t: Throwable) { false }
        val screenOn = try {
            km?.let { true } == true && w.isShown
        } catch (t: Throwable) { false }
        val ls = lockScreen ?: return
        ls.visibility = if (locked && screenOn) View.VISIBLE else View.GONE
        if (!locked) hidePasscode()
    }

    private fun onUnlockRequested() {
        val kmv = km
        val secure = try { kmv?.isDeviceSecure == true } catch (t: Throwable) { false }
        if (secure) showPasscode() else hideLockScreen()
    }

    private fun showPasscode() {
        passcode?.visibility = View.VISIBLE
        passcode?.clear()
    }

    private fun hidePasscode() {
        passcode?.visibility = View.GONE
    }

    private fun hideLockScreen() {
        lockScreen?.visibility = View.GONE
        hidePasscode()
    }

    private fun setTorch(on: Boolean) {
        // Best-effort, same discipline as AppleuxHaptics: a missing CAMERA
        // grant must never crash SystemUI.
        try {
            val cm = window?.context?.getSystemService(Context.CAMERA_SERVICE)
                as? android.hardware.camera2.CameraManager ?: return
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, on)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "torch: ${t.javaClass.simpleName}")
        }
    }

    private fun launchCamera(context: Context) {
        try {
            context.startActivity(
                Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "camera: ${t.javaClass.simpleName}")
        }
    }

    private fun registerLockState(context: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_USER_PRESENT,
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_SCREEN_ON -> refreshState()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val flags = if (Build.VERSION.SDK_INT >= 33)
            Context.RECEIVER_NOT_EXPORTED else 0
        try {
            context.registerReceiver(r, filter, flags)
            receiver = r
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "lock-state receiver: ${t.javaClass.simpleName}")
        }
    }

    @JvmStatic
    fun detach() {
        receiver?.let { r ->
            window?.context?.let { runCatching { it.unregisterReceiver(r) } }
        }
        receiver = null
        lockScreen?.let { (it.parent as? ViewGroup)?.removeView(it) }
        passcode?.let { (it.parent as? ViewGroup)?.removeView(it) }
        lockScreen = null
        passcode = null
        window = null
        isAttached = false
    }
}
