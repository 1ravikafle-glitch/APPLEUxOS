package com.appleux.settings.performance

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.performance.controller.AppleuxPerformanceController
import com.appleux.performance.controller.MonitoringLevel
import com.appleux.performance.quality.AppleuxQualityLevel
import com.appleux.settings.R

class PerformanceSettingsActivity : AppCompatActivity(),
    AppleuxPerformanceController.PerformanceListener {

    private lateinit var qualityText: TextView
    private lateinit var scaleText: TextView
    private lateinit var monitoringText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.performance_settings)

        qualityText = findViewById(R.id.qualityValue)
        scaleText = findViewById(R.id.scaleValue)
        monitoringText = findViewById(R.id.monitoringValue)

        AppleuxPerformanceController.getInstance(this).registerListener(this)
        renderCurrentState()
    }

    override fun onDestroy() {
        AppleuxPerformanceController.getInstance(this).unregisterListener(this)
        super.onDestroy()
    }

    private fun renderCurrentState() {
        qualityText.text = AppleuxPerformanceController.getCurrentQuality().label
        scaleText.text = String.format(
            "%.0f%%",
            AppleuxPerformanceController.getCurrentRenderScale() * 100f
        )
        monitoringText.text = describeLevel(
            AppleuxPerformanceController.getCurrentMonitoringLevel()
        )
    }

    private fun describeLevel(level: MonitoringLevel): String = when (level) {
        MonitoringLevel.LEVEL_0_NORMAL -> "Normal"
        MonitoringLevel.LEVEL_1_WARNING -> "Warning"
        MonitoringLevel.LEVEL_2_DIAGNOSTIC -> "Diagnostic"
        MonitoringLevel.LEVEL_3_EMERGENCY -> "Emergency"
    }

    override fun onQualityChanged(level: AppleuxQualityLevel) {
        qualityText.text = level.label
    }

    override fun onRenderScaleChanged(scale: Float) {
        scaleText.text = String.format("%.0f%%", scale * 100f)
    }

    override fun onMonitoringLevelChanged(level: MonitoringLevel) {
        monitoringText.text = describeLevel(level)
    }
}