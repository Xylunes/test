package com.valorantsens

/**
 * 单次瞄准行为数据点（手指触控）
 */
data class TouchDataPoint(
    val timestamp: Long = System.currentTimeMillis(),

    // 速度
    val peakSpeedPxPerMs: Float,
    val avgSpeedPxPerMs: Float,
    val totalDistancePx: Float,
    val durationMs: Long,

    // 瞄准偏差
    val intentAngleDeg: Float,
    val avgDeviationDeg: Float,
    val maxDeviationDeg: Float,
    val overshootPx: Float,        // 正=冲过头, 负=没到位

    // 速度节奏
    val decelerationRatio: Float,
    val speedConsistency: Float,

    // 当时配置
    val hipfireSens: Float,
    val aimSightSens: Float,
    val wasAiming: Boolean
)

/**
 * 单次陀螺仪瞄准数据点
 * 记录一段陀螺仪辅助动作（从开始旋转到稳定）
 */
data class GyroDataPoint(
    val timestamp: Long = System.currentTimeMillis(),

    val avgRotationRate: Float,    // 平均旋转速率（rad/s）
    val peakRotationRate: Float,   // 峰值旋转速率
    val durationMs: Long,          // 持续时间

    // 稳定性：结束时旋转速率是否趋近于0（越小越稳）
    val endRotationRate: Float,

    // 抖动量：采样间方差（越小越稳定）
    val rotationVariance: Float,

    val gyroSens: Float,           // 当时的陀螺仪灵敏度
    val wasAiming: Boolean
)

/**
 * 实时统计窗口
 */
data class RealtimeStats(
    val hipfire:      SensStats  = SensStats(),
    val aimSight:     SensStats  = SensStats(),
    val gyro:         GyroStats  = GyroStats(),   // ← 新增陀螺仪统计
    val lastUpdated:  Long       = 0L,
    val totalSamples: Int        = 0
)

data class SensStats(
    val sampleCount: Int       = 0,
    val avgDeviationDeg: Float = 0f,
    val overshootRate: Float   = 0f,
    val avgSpeed: Float        = 0f,
    val speedVariance: Float   = 0f,
    val adjustDirection: Int   = 0,   // +1=调高, -1=调低, 0=刚好
    val adjustAmount: Float    = 0f
)

/**
 * 陀螺仪统计
 */
data class GyroStats(
    val sampleCount: Int           = 0,
    val avgRotationRate: Float     = 0f,   // 平均手腕旋转速率
    val avgEndStability: Float     = 0f,   // 平均结束稳定性（越低越稳）
    val avgVariance: Float         = 0f,   // 平均抖动方差
    // 是否适合开陀螺仪的判断
    // 手腕稳定（方差小）→ 适合开；抖动大 → 不建议
    val isStableEnough: Boolean    = false,
    val recommendedSens: Float     = 0.8f  // 基于稳定性计算的推荐强度
)
