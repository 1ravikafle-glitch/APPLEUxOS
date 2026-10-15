package org.ioslike.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.WallpaperSource
import org.ioslike.ui.theme.readSafeInsets

/** The home screen. Declared as HOME in the manifest. */
class LauncherActivity : ComponentActivity() {
    private lateinit var repo: AppRepo
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = repo.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        repo = AppRepo(this).also { it.load() }
        val wallpaper = WallpaperSource.load(this)
        setContent {
            CompositionLocalProvider(LocalSafeInsets provides readSafeInsets()) { LauncherRoot(repo, wallpaper) { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() { unregisterReceiver(pkgReceiver); super.onStop() }
}
