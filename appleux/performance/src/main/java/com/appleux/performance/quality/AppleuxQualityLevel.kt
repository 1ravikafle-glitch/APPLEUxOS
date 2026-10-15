package com.appleux.performance.quality

enum class AppleuxQualityLevel(val level: Int, val scale: Float, val label: String) {
    QUALITY_100(6, 1.00f, "100%"),
    QUALITY_95(5, 0.95f, "95%"),
    QUALITY_90(4, 0.90f, "90%"),
    QUALITY_85(3, 0.85f, "85%"),
    QUALITY_80(2, 0.80f, "80%"),
    QUALITY_75(1, 0.75f, "75%"),
    QUALITY_70(0, 0.70f, "70%");

    fun isMaximum(): Boolean = this == QUALITY_100
    fun isMinimum(): Boolean = this == QUALITY_70

    companion object {
        fun fromScale(scale: Float): AppleuxQualityLevel {
            val clamped = scale.coerceIn(0.70f, 1.0f)
            return values().findLast { it.scale <= clamped + 0.001f } ?: QUALITY_70
        }
    }
}
