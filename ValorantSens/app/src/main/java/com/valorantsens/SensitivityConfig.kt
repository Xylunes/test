package com.valorantsens

import android.content.Context
import android.content.SharedPreferences

/**
 * 灵敏度配置 —— 完全对齐游戏内实际参数
 *
 * 滑屏设置：
 *   - slideOverall : 滑屏灵敏度一键调节 (1~200%)
 *   - slideVertical: 垂直灵敏度 Y轴     (1~200%)
 *
 * 陀螺仪设置：
 *   - gyroMode     : 陀螺仪开关 (OFF / AIM_ONLY / ALWAYS)
 *   - gyroOverall  : 陀螺仪灵敏度一键调节 (1~200%)
 *   - gyroVertical : 陀螺仪垂直灵敏度 Y轴 (1~200%)
 */
data class SensitivityConfig(

    // ── 滑屏 ──────────────────────────────────────
    val slideOverall:  Int = 114,   // 滑屏一键调节 %
    val slideVertical: Int = 28,    // 滑屏垂直 Y轴 %

    // ── 陀螺仪 ────────────────────────────────────
    val gyroMode:     GyroMode = GyroMode.OFF,
    val gyroOverall:  Int = 172,    // 陀螺仪一键调节 %
    val gyroVertical: Int = 49      // 陀螺仪垂直 Y轴 %
) {
    enum class GyroMode { OFF, AIM_ONLY, ALWAYS }

    // 便捷属性
    val gyroEnabled: Boolean get() = gyroMode != GyroMode.OFF

    companion object {
        private const val PREFS = "sensitivity_config"

        fun load(context: Context): SensitivityConfig {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return SensitivityConfig(
                slideOverall  = p.getInt("slideOverall",  114),
                slideVertical = p.getInt("slideVertical", 28),
                gyroMode      = GyroMode.values()[p.getInt("gyroMode", 0)],
                gyroOverall   = p.getInt("gyroOverall",  172),
                gyroVertical  = p.getInt("gyroVertical", 49)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("slideOverall",  slideOverall)
            .putInt("slideVertical", slideVertical)
            .putInt("gyroMode",     gyroMode.ordinal)
            .putInt("gyroOverall",  gyroOverall)
            .putInt("gyroVertical", gyroVertical)
            .apply()
    }
}
