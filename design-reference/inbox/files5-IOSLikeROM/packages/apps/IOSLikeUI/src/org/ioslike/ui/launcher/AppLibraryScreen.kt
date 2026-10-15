package org.ioslike.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.noRippleClickable
import org.ioslike.ui.theme.squircle

/** Search field + 2-column grid of glass category folders (2x2 mini icons). Tap a folder to open it. */
@Composable
fun AppLibraryScreen(repo: AppRepo) {
    val apps by repo.apps.collectAsState()
    val cats by repo.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var openIndex by remember { mutableStateOf(-1) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = LocalSafeInsets.current.top + Tokens.Space.contentTopGap)) {
            Row(Modifier.fillMaxWidth().height(48.dp).glass(CircleShape).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner -> if (query.isEmpty()) BasicText("App Library", style = Glass.body); inner() },
                )
            }
            Box(Modifier.height(18.dp))
            if (query.isNotBlank()) {
                val hits = apps.filter { it.label.contains(query, ignoreCase = true) }
                LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(20.dp)) { items(hits) { AppCell(it, repo) } }
            } else {
                LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(cats) { i, c -> Folder(c, repo) { openIndex = i } }
                }
            }
        }
        cats.getOrNull(openIndex)?.let { (name, list) ->
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).noRippleClickable { openIndex = -1 }, contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(24.dp).glass(squircle(36.dp)).padding(20.dp).noRippleClickable { }) {
                    BasicText(name, style = Glass.bodyBold)
                    Box(Modifier.height(14.dp))
                    LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(list) { AppCell(it, repo) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Folder(cat: Pair<String, List<AppInfo>>, repo: AppRepo, onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).glass(squircle(Tokens.Radius.folder)).noRippleClickable(onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            cat.second.take(4).chunked(2).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { a -> Box(Modifier.noRippleClickable { repo.launch(a) }) { AppIcon(a, 62.dp) } } }
            }
        }
        BasicText(cat.first, style = Glass.bodyBold, modifier = Modifier.padding(top = 8.dp))
    }
}
