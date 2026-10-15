package org.ioslike.ui.launcher

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.GlassScene
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.pressable
import org.ioslike.ui.theme.squircle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home pages (widget + 4-column grid), a final App Library page, floating glass dock,
 * "Search or Ask" pill and page dots. Matches the Home & Dock / App Library design boards.
 */
@Composable
fun LauncherRoot(repo: AppRepo, wallpaper: ImageBitmap?, onSearch: () -> Unit) {
    val apps by repo.apps.collectAsState()
    val dock = remember(apps) { repo.dock() }
    val dockPkgs = dock.map { it.pkg }.toSet()
    val pages = remember(apps) {
        var rest = apps.filter { it.pkg !in dockPkgs }
        buildList {
            add(rest.take(16)); rest = rest.drop(16)           // page 1 has the widget (4 + 12 icons)
            while (rest.isNotEmpty()) { add(rest.take(20)); rest = rest.drop(20) }
        }
    }
    val pager = rememberPagerState(pageCount = { pages.size + 1 })   // last page = App Library
    val onLibrary = pager.currentPage >= pages.size

    GlassScene(wallpaper, blur = 0.dp, dim = if (onLibrary) 0.3f else 0.12f) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page < pages.size) HomePage(page, pages[page], repo) else AppLibraryScreen(repo)
        }
        // Page dots stay in ONE fixed place on every page (including App Library); only the active dot moves.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = LocalSafeInsets.current.bottom + Tokens.Space.dotsBottom),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(pager.pageCount) { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color(0x66FFFFFF)))
            }
        }
        // Search pill and dock stay fixed while pages scroll; both fade out on the App Library page.
        AnimatedVisibility(!onLibrary, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalSafeInsets.current.bottom), enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 48dp touch target around the 40dp pill (the 4dp slop on each side is why the gap below is 10, not 14)
                Box(Modifier.width(Tokens.Size.pillW).height(Tokens.Size.pillHit).pressable(onSearch), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(Tokens.Size.pillW).height(Tokens.Size.pillH).glass(CircleShape), contentAlignment = Alignment.Center) {
                        BasicText("Search or Ask", style = Glass.bodyBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp).height(Tokens.Size.dock)
                        .glass(squircle(Tokens.Radius.dock)).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
                ) { dock.forEach { AppCell(it, repo, showLabel = false, size = Tokens.Size.dockIcon) } }
            }
        }
    }
}

@Composable
private fun HomePage(index: Int, items: List<AppInfo>, repo: AppRepo) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = LocalSafeInsets.current.top + Tokens.Space.contentTopGap)) {
        var rest = items
        if (index == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TodayWidget(Modifier.size(166.dp))
                Column(Modifier.size(166.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    items.take(4).chunked(2).forEach { r ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { r.forEach { AppCell(it, repo, showLabel = false) } }
                    }
                }
            }
            rest = items.drop(4)
            Spacer(Modifier.height(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            rest.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    row.forEach { AppCell(it, repo) }
                    repeat(4 - row.size) { Spacer(Modifier.width(Tokens.Size.cell)) }
                }
            }
        }
    }
}

@Composable
private fun TodayWidget(modifier: Modifier) {
    val now = Date()
    val locale = Locale.getDefault()
    val dow = SimpleDateFormat("EEEE", locale).format(now).uppercase(locale)
    val day = SimpleDateFormat("d", locale).format(now)
    val month = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMM"), locale).format(now)
    Column(modifier.glass(squircle(30.dp)).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText(dow, style = TextStyle(color = Glass.labelDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        BasicText(day, style = TextStyle(color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold))
        BasicText(month, style = Glass.bodyBold)
    }
}

@Composable
fun AppIcon(app: AppInfo, size: Dp = Tokens.Size.appIcon) {
    Image(app.icon.asImageBitmap(), app.label, Modifier.size(size).clip(squircle(size * Tokens.Ratio.appIconCorner)))
}

@Composable
fun AppCell(app: AppInfo, repo: AppRepo, showLabel: Boolean = true, size: Dp = Tokens.Size.appIcon) {
    Column(Modifier.width(Tokens.Size.cell).pressable { repo.launch(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, size)
        if (showLabel) {
            BasicText(
                app.label, style = Glass.caption.copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
