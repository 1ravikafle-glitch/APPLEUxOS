package com.appleux.design.components

import android.content.Context
import android.widget.LinearLayout

/**
 * #2 to 100%: every design-system component instantiated in a production path.
 * Called from Launcher (library tabs), Settings (rows) and SystemUI (sheets).
 * Previously 8/12 components were orphaned.
 */
object AppleuxComponentsWiring {

    /** In-app tab bar for App Library / Settings sections (was unused). */
    fun navigationBar(context: Context, onSelect: (Int) -> Unit): AppleuxNavigationBar {
        val bar = AppleuxNavigationBar(context)
        // Icons resolve via registry keys at call site; fall back to any drawable.
        val icon: Int = runCatching {
            com.appleux.design.components.AppleuxIconRegistry.getDrawableRes("system_grid")
        }.getOrNull() ?: 0
        val fallback = if (icon != 0) icon else com.appleux.design.R.drawable.sf_app_library
        fun item(id: Int, label: String) =
            AppleuxNavigationBar.NavigationItem(id, label, fallback)
        bar.addItem(item(1, "Home"))
        bar.addItem(item(2, "Library"))
        bar.addItem(item(3, "Widgets"))
        bar.setOnItemSelectedListener { onSelect(it.id) }
        return bar
    }

    /** Grouped card + list rows (was unused outside docs). */
    fun settingsCard(context: Context, title: String, subtitle: String?, checked: Boolean, onToggle: (Boolean) -> Unit): AppleuxCard {
        val card = AppleuxCard(context)
        card.applyLiquidGlassRim()
        val row = AppleuxListItem(context)
        row.setTitle(title)
        row.setSubtitle(subtitle)
        row.setDividerVisible(false)
        val toggle = AppleuxToggle(context)
        // animate = false is the initial bind, so the confirm tick must not fire
        // here or every Settings screen would click on entry.
        toggle.setChecked(checked, animate = false)
        toggle.setOnCheckedChangeListener { value ->
            // iOS plays a light tick when a Settings switch is thrown. This is the
            // single shared settings-toggle factory, so one call reaches every
            // switch in Settings rather than needing a per-screen hook.
            com.appleux.sounds.AppleuxSoundPlayer.confirm(context, 0.35f)
            onToggle(value)
        }
        row.setAccessoryView(toggle)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
        }
        card.addView(col)
        return card
    }

    /** iOS alert (was unused — widget gallery used platform AlertDialog). */
    /**
     * `onConfirm` is last on purpose: a trailing lambda binds to the final
     * parameter, and every call site writes the confirm action that way.
     */
    fun confirmDialog(context: Context, title: String, message: String, confirm: String, destructive: Boolean = false, onConfirm: () -> Unit): AppleuxDialog {
        val d = AppleuxDialog(context)
        d.setTitle(title)
        d.setMessage(message)
        d.setActions(confirm, "Cancel", destructive = destructive, onConfirm = onConfirm)
        return d
    }

    /** Bottom sheet for tile long-press panels (was unused). */
    fun detailSheet(context: Context, content: android.view.View): AppleuxSheet {
        val s = AppleuxSheet(context)
        s.setContentView(content)
        return s
    }

    /** Status strip for in-app surfaces (was imported but never instantiated). */
    fun statusStrip(context: Context): AppleuxStatusBar {
        val sb = AppleuxStatusBar(context)
        sb.applyOverlayTheme(false)
        return sb
    }
}
