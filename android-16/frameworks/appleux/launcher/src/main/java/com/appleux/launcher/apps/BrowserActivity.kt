package com.appleux.launcher.apps

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.appleux.design.components.AppleuxGlass
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

/**
 * iOS 27 Browser UI for AppleUX.
 *
 * Design: fullscreen WebView with a floating glass address bar at top,
 * bottom toolbar with back/forward/share/tabs, and circular glass buttons
 * for tab switcher (bottom-right) and bookmarks (bottom-left).
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var tabSwitcher: ImageButton
    private lateinit var bookmarksButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var tabsButton: ImageButton
    private var tabCount = 1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ColorTokens.systemBackground)
        }

        // Fullscreen WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    updateAddressBar(url ?: "")
                }
            }
            loadUrl("https://www.apple.com")
        }

        // Pull-to-refresh wrapper
        val refreshLayout = SwipeRefreshLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setColorSchemeColors(ColorTokens.systemBlue)
            addView(webView)
            setOnRefreshListener {
                webView.reload()
                isRefreshing = false
            }
        }

        root.addView(refreshLayout)

        // Top address bar
        val addressBarContainer = createAddressBar()
        root.addView(addressBarContainer)

        // Bottom toolbar
        val toolbar = createToolbar()
        root.addView(toolbar)

        // Tab switcher (bottom-right)
        val tabSwitcherContainer = createTabSwitcher()
        root.addView(tabSwitcherContainer)

        // Bookmarks (bottom-left)
        val bookmarksContainer = createBookmarksButton()
        root.addView(bookmarksContainer)

        setContentView(root)
        applyChrome()
    }

    private fun applyChrome() {
        AppleuxTheme.applyToActivity(this)
        AppleuxRuntimeConfig.reload(this)
        AppleuxTheme.isDarkMode = AppleuxRuntimeConfig.isDarkModeEnabled
        AppleuxTheme.applySystemBars(this)
    }

    private fun createAddressBar(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, (SpacingTokens.INSETS_XXXL4 * density).toInt(), margin, 0)
            }
        }

        // Glass pill background
        val glassBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (36 * density).toInt()
            )
            background = createGlassPillBackground(36f)
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
        }

        // Address bar text
        addressBar = EditText(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                val padH = (SpacingTokens.INSETS_MD * density).toInt()
                setPadding(padH, 0, padH, 0)
            }
            hint = "Search or enter website name"
            setHintTextColor(ColorTokens.tertiaryLabel)
            setTextColor(ColorTokens.label)
            textSize = TypographyTokens.callout.size
            isSingleLine = true
            gravity = Gravity.CENTER
            setBackgroundResource(0)
            setOnEditorActionListener { _, _, _ ->
                loadUrlFromAddressBar()
                true
            }
        }

        container.addView(glassBg)
        container.addView(addressBar)
        return container
    }

    private fun createToolbar(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        // Glass toolbar background
        val toolbarBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (52 * density).toInt()
            ).apply {
                gravity = Gravity.BOTTOM
            }
            background = createGlassToolbarBackground()
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
        }

        // Toolbar items row
        val itemsRow = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        backButton = createToolbarButton("system_chevron_left", "Back") {
            if (webView.canGoBack()) webView.goBack()
        }
        forwardButton = createToolbarButton("system_chevron_right", "Forward") {
            if (webView.canGoForward()) webView.goForward()
        }
        shareButton = createToolbarButton("system_share", "Share") {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, webView.url ?: "")
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }
        tabsButton = createToolbarButton("system_grid", "Tabs") {
            showTabSwitcher()
        }

        itemsRow.addView(backButton)
        itemsRow.addView(forwardButton)
        itemsRow.addView(shareButton)
        itemsRow.addView(tabsButton)

        container.addView(toolbarBg)
        container.addView(itemsRow)
        return container
    }

    private fun createTabSwitcher(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(0, 0, margin, (SpacingTokens.INSETS_XXXL7 * density).toInt())
            }
        }

        tabSwitcher = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (44 * density).toInt(),
                (44 * density).toInt()
            )
            background = createGlassCircleBackground(44f)
            setImageDrawable(getIcon("system_grid"))
            setColorFilter(ColorTokens.label)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val pad = (SpacingTokens.INSETS_MD * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "Tab Switcher"
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
            setOnClickListener { showTabSwitcher() }
        }

        container.addView(tabSwitcher)
        return container
    }

    private fun createBookmarksButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, 0, 0, (SpacingTokens.INSETS_XXXL7 * density).toInt())
            }
        }

        bookmarksButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (44 * density).toInt(),
                (44 * density).toInt()
            )
            background = createGlassCircleBackground(44f)
            setImageDrawable(getIcon("system_star"))
            setColorFilter(ColorTokens.label)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val pad = (SpacingTokens.INSETS_MD * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "Bookmarks"
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
            setOnClickListener { showBookmarks() }
        }

        container.addView(bookmarksButton)
        return container
    }

    private fun createToolbarButton(iconName: String, contentDesc: String, onClick: () -> Unit): ImageButton {
        val density = resources.displayMetrics.density
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (44 * density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                val margin = (SpacingTokens.INSETS_SM * density).toInt()
                setMargins(margin, 0, margin, 0)
            }
            background = null
            setImageDrawable(getIcon(iconName))
            setColorFilter(ColorTokens.label)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = contentDesc
            setOnClickListener { onClick() }
        }
    }

    private fun createGlassPillBackground(heightDp: Float): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = heightDp * density / 2f
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
        }
    }

    private fun createGlassCircleBackground(sizeDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
        }
    }

    private fun createGlassToolbarBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
        }
    }

    private fun getIcon(name: String): android.graphics.drawable.Drawable? {
        return AppleuxIconRegistry.getDrawableRes(name)?.let {
            ContextCompat.getDrawable(this, it)
        }
    }

    private fun loadUrlFromAddressBar() {
        var url = addressBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        webView.loadUrl(url)
    }

    private fun updateAddressBar(url: String) {
        if (!::addressBar.isInitialized) return
        addressBar.setText(url)
    }

    private fun showTabSwitcher() {
        tabCount++
        // In a full implementation, this would show a tab grid overlay
        // For now, just refresh to show the concept
        webView.reload()
    }

    private fun showBookmarks() {
        // In a full implementation, this would show a bookmarks panel
        // For now, navigate to a bookmarks URL as placeholder
        webView.loadUrl("https://www.apple.com")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
