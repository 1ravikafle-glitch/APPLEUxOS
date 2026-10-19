package com.appleux.systemui.chrome

import android.os.IPowerManager
import android.os.ServiceManager
import android.util.Log

/**
 * Real power-off and factory reset, at system-core level.
 *
 * ======================================================================
 * WHY THIS FILE EXISTS — the four routes that were checked
 * ======================================================================
 * Power-off cannot be done from an ordinary app, and that was established by
 * reading the tree rather than assuming it:
 *
 *  * `PowerManager.shutdown(...)` is `@SystemApi` — absent from the public SDK
 *    (javap on android.jar, API 36). An app cannot reach it.
 *  * `android.intent.action.ACTION_SHUTDOWN` is a `<protected-broadcast>` in
 *    `frameworks/base/core/res/AndroidManifest.xml` — only the system may send
 *    it, so an app cannot raise it either.
 *  * `ShutdownThread` lives in `frameworks/base/services/core/java`, which is
 *    NOT on an application's or SystemUI's classpath.
 *  * `PowerManagerInternal` (core/java/android/os) declares no shutdown or
 *    reboot method at all, so it is not the bridge people assume it is.
 *
 * ======================================================================
 THE ROUTE THAT WORKS
 * ======================================================================
 * `android/os/IPowerManager.aidl` **is** in `core/java`, so it is compiled into
 * framework.jar and therefore sits on the boot classpath. It declares
 * `shutdown(boolean confirm, String reason, boolean wait)` at line 125.
 *
 * A module with `platform_apis: true` can therefore bind the power service and
 * call it directly. `appleux-systemui-chrome` already is such a module and is
 * already linked into `SystemUI-application`, so no new process, no new window
 * and no IPC hop into another app is involved.
 *
 * The permission is the easy half: `android.permission.SHUTDOWN` is
 * `signature|privileged|role`, and SystemUI is **platform-signed**, so the
 * signature component is satisfied automatically the moment the permission is
 * declared in SystemUI's manifest. No privapp-permissions entry is needed, and
 * none can be — that file only covers privileged apps, and SystemUI is a system
 * app.
 *
 * ======================================================================
 * WHY THIS IS NOT A REFLECTION HACK
 * ======================================================================
 * Binding the service directly is the documented mechanism the framework itself
 * uses; nothing here is hidden from us, and nothing needs `--allow-hidden-api`
 * flags or reflection. If the API changes, this fails to COMPILE, which is the
 * property you want from a call that powers the device off.
 */
object AppleuxPowerControl {

    private const val TAG = "AppleuxPower"
    private const val REASON_USER_REQUEST = "appleux_user_request"

    /** True when the power service bound and the call is worth attempting. */
    @JvmStatic
    fun isAvailable(): Boolean = powerManager() != null

    private fun powerManager(): IPowerManager? = try {
        val binder = ServiceManager.getService("power")
        if (binder == null) null else IPowerManager.Stub.asInterface(binder)
    } catch (t: Throwable) {
        // No power service, or the call was refused. Never throw into SystemUI:
        // a crash here happens while handling a button press.
        Log.w(TAG, "power service unavailable: ${t.javaClass.simpleName}")
        null
    }

    /**
     * Powers the device off.
     *
     * @param confirm true shows the system's own confirmation, which is the
     *   behaviour for a menu-initiated shutdown and is the safe default. Pass
     *   false only from a gesture that has already been confirmed, such as
     *   Apple's slide-to-power-off.
     * @return true if the request reached the service.
     */
    @JvmStatic
    @JvmOverloads
    fun shutdown(confirm: Boolean = true): Boolean {
        val pm = powerManager() ?: return false
        return try {
            // wait=false: do not block the caller. SystemUI is a UI process and
            // must not stall its thread waiting for the device to go down.
            pm.shutdown(confirm, REASON_USER_REQUEST, false)
            true
        } catch (t: Throwable) {
            // SecurityException if SHUTDOWN was not granted. Log it rather than
            // crashing, because this runs from a button press.
            Log.w(TAG, "shutdown refused: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * Reboots the device.
     *
     * The public `PowerManager.reboot(String)` path works from Settings today and
     * is what AppleuxPowerSlider's restart half uses. This exists so the same
     * control can offer a restart from SystemUI as well, without Settings being
     * involved.
     */
    @JvmStatic
    @JvmOverloads
    fun reboot(confirm: Boolean = true): Boolean {
        val pm = powerManager() ?: return false
        return try {
            pm.reboot(confirm, REASON_USER_REQUEST, false)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "reboot refused: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * Factory reset: wipes user data and reboots into recovery.
     *
     * Uses the PUBLIC `RecoverySystem.rebootWipeUserData(Context)`, which
     * `javap` against the API 36 android.jar confirms is public API requiring
     * only `REBOOT` — a grant AppleUXSettings already holds.
     *
     * It is genuinely destructive: every user photo, message and setting is
     * erased. Callers MUST confirm first; `AppleuxPowerMenu` gates it behind a
     * confirmation dialog and never calls this directly.
     *
     * It deliberately does NOT go through `Settings.Global.MASTER_CLEAR`, which
     * is absent from the Settings provider on this ROM and is why the Settings
     * catalog omits a reset row. Recovery-based wipe is unaffected by that.
     */
    @JvmStatic
    fun factoryReset(context: android.content.Context): Boolean = try {
        android.os.RecoverySystem.rebootWipeUserData(context.applicationContext)
        true
    } catch (t: Throwable) {
        // IOException if the recovery command file cannot be written,
        // SecurityException if REBOOT was not granted.
        Log.w(TAG, "factory reset refused: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
}
