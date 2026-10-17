package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/**
 * iOS 27 connectivity cluster: 2x2 glass card (Airplane / Cellular / Wi-Fi /
 * Bluetooth) exactly as in guide screenshot 7511c37e top-left group.
 * Taps disconnect (radio stays on) — the service owns the platform calls.
 */
class AppleuxConnectivityCluster @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {

    private val grid = GridLayout(context).apply { columnCount = 2; rowCount = 2 }
    private val wifiBtn: AppleuxControlTile
    private val btBtn: AppleuxControlTile
    private val airplaneBtn: AppleuxControlTile
    private val cellBtn: AppleuxControlTile

    var onWifi: (() -> Unit)? = null
    var onBluetooth: (() -> Unit)? = null
    var onAirplane: (() -> Unit)? = null
    var onCellular: (() -> Unit)? = null

    init {
        applyLiquidGlassRim()
        val mk = { id: String, name: String, icon: String, fn: () -> Unit ->
            AppleuxControlTile(
                context,
                AppleuxControlCenter.ControlTile(id, name, iconName = icon, isToggle = true, onClick = fn)
            ).apply {
                // Cluster cells match the grid discs (single constant).
                val d = (48 * resources.displayMetrics.density).toInt()
                val cellLp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f))
                cellLp.width = d
                cellLp.height = d
                setLayoutParams(cellLp)
            }
        }
        airplaneBtn = mk("airplane", "Airplane", "system_airplane") { onAirplane?.invoke() }
        cellBtn = mk("cellular", "Cellular", "system_cellular") { onCellular?.invoke() }
        wifiBtn = mk("wifi", "Wi-Fi", "system_wifi") { onWifi?.invoke() }
        btBtn = mk("bluetooth", "Bluetooth", "system_bluetooth") { onBluetooth?.invoke() }
        for (v in listOf(airplaneBtn, cellBtn, wifiBtn, btBtn)) {
            v.setOnClickListener { (v as AppleuxControlTile).let { } }
            grid.addView(v)
        }
        // Route tile taps through cluster callbacks (tile itself calls its own onClick).
        addView(grid.apply { setPadding(dp(8), dp(8), dp(8), dp(8)) })
        // Re-wire: tiles already invoke their onClick which forwards.
        airplaneBtn.setOnClickListener { onAirplane?.invoke() }
        cellBtn.setOnClickListener { onCellular?.invoke() }
        wifiBtn.setOnClickListener { onWifi?.invoke() }
        btBtn.setOnClickListener { onBluetooth?.invoke() }
    }

    fun setStates(wifi: Boolean, bt: Boolean, airplane: Boolean, cellular: Boolean) {
        wifiBtn.setActive(wifi); btBtn.setActive(bt)
        airplaneBtn.setActive(airplane); cellBtn.setActive(cellular)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
