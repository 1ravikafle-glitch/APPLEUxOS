package com.appleux.settings.detail

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.settings.R

/**
 * Shared chrome for every owned Settings detail page.
 *
 * Detail pages used to be one-off activities that each rebuilt the title bar
 * (and several never finished it). This owns the two things they all need:
 * the iOS navigation bar with a working back affordance, and a content slot to
 * inflate into.
 *
 * Back follows the platform rule rather than an assumption: [canGoBack] is
 * false on a root entry, where back finishes the activity instead of popping a
 * stack that does not exist.
 */
abstract class SettingsDetailActivity : AppCompatActivity() {

    // Public: the shared DetailKit chrome helper attaches the page body
    // here; protected would lock it out of the same package.
    val content: FrameLayout by lazy { findViewById(R.id.detailContent) }

    /** Subclasses set this to the page title shown in the nav bar. */
    protected abstract val titleText: String

    /**
     * Whether this page sits above another in our own task. Defaults to true;
     * root entries override to false so back exits instead of popping nothing.
     */
    protected open val canGoBack: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B25: the nav bar must sit BELOW the status band (GT shows the
        // inline title below it, and the measured old page had the title at
        // y=0). This also hides the stock bar — one chrome, ours.
        com.appleux.design.theme.AppleuxTheme.applySystemBars(this)
        setContentView(R.layout.settings_detail)
        findViewById<TextView>(R.id.detailTitle).text = titleText

        val back = findViewById<TextView>(R.id.detailBack)
        back.visibility = if (canGoBack) View.VISIBLE else View.INVISIBLE
        back.setOnClickListener { goBack() }
        // Same target as the chevron, for accessibility services that activate
        // the nav bar rather than the glyph.
        back.contentDescription = getString(R.string.settings_back)

        onContentReady()
    }

    /** Build the page body. Called once the content slot exists. */
    protected abstract fun onContentReady()

    private fun goBack() {
        if (canGoBack && supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    /** Up-delegates to the same rule as the chevron. */
    override fun onSupportNavigateUp(): Boolean {
        goBack()
        return true
    }
}