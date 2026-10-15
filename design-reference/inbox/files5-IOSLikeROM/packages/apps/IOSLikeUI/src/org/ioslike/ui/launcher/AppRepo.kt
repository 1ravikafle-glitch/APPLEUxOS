package org.ioslike.ui.launcher

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppInfo(val pkg: String, val component: ComponentName, val label: String, val category: Int, val icon: Bitmap)

/** Reads launchable apps, builds App Library categories and the default dock apps. */
class AppRepo(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    private val _categories = MutableStateFlow<List<Pair<String, List<AppInfo>>>>(emptyList())
    val categories: StateFlow<List<Pair<String, List<AppInfo>>>> = _categories.asStateFlow()

    fun load() {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(main, 0).map { ri ->
            val ai = ri.activityInfo
            AppInfo(ai.packageName, ComponentName(ai.packageName, ai.name), ri.loadLabel(pm).toString(),
                ai.applicationInfo.category, ri.loadIcon(pm).toBmp(144))
        }.filter { it.component.className != "org.ioslike.ui.launcher.LauncherActivity" && it.pkg != "com.android.settings" }.sortedBy { it.label.lowercase() }
        _apps.value = all
        _categories.value = buildCategories(all)
    }

    fun launch(a: AppInfo) {
        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Dock: the user's default phone, messages, browser and camera apps. */
    fun dock(): List<AppInfo> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull { i ->
            val r = pm.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return@mapNotNull null
            _apps.value.firstOrNull { it.pkg == r.packageName }
        }.distinctBy { it.pkg }
    }

    private fun buildCategories(all: List<AppInfo>): List<Pair<String, List<AppInfo>>> {
        fun cat(a: AppInfo) = when (a.category) {
            ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS -> "Social"
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> "Entertainment"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> "Utilities"
        }
        val grouped = all.groupBy(::cat)
        val added = all.sortedByDescending { try { pm.getPackageInfo(it.pkg, 0).firstInstallTime } catch (_: Exception) { 0L } }.take(8)
        val used = try {
            val now = System.currentTimeMillis()
            val stats = ctx.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(now - 7 * 86_400_000L, now)
            all.filter { stats[it.pkg] != null }.sortedByDescending { stats[it.pkg]!!.lastTimeUsed }.take(8)
        } catch (_: Exception) { emptyList() }
        return listOf("Suggestions" to used, "Recently Added" to added) +
            listOf("Social", "Entertainment", "Productivity", "Utilities").mapNotNull { n -> grouped[n]?.let { n to it } }
            .filter { it.second.isNotEmpty() }
    }

    private fun Drawable.toBmp(size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size); draw(Canvas(b)); return b
    }
}
