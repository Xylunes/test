package com.valorantsens

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.*

/**
 * 实时触控数据采集器
 *
 * 工作流程：
 * ACTION_DOWN  → beginStroke()  开始记录一次瞄准
 * ACTION_MOVE  → addSample()    逐帧记录轨迹点
 * ACTION_UP    → endStroke()    完成一次瞄准，计算偏差，实时更新推荐值
 */
class DataCollector(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sens_realtime", Context.MODE_PRIVATE)

    // 当前笔画的逐帧数据
    private val strokeSamples = mutableListOf<StrokeSample>()
    private var strokeStartTime = 0L
    private var strokeStartX = 0f
    private var strokeStartY = 0f

    // 滑动窗口：最近 WINDOW 次瞄准（分腰射/开镜两个队列）
    private val hipfireWindow = ArrayDeque<TouchDataPoint>()
    private val aimSightWindow = ArrayDeque<TouchDataPoint>()
    private val gyroWindow    = ArrayDeque<GyroDataPoint>()   // 陀螺仪数据窗口
    private val WINDOW_SIZE = 30  // 保留最近30次

    // 实时推荐值（对外暴露，供悬浮窗读取）
    @Volatile var realtimeStats: RealtimeStats = RealtimeStats()
        private set

    // 监听器：推荐值更新时回调（悬浮窗用）
    var onRecommendationUpdated: ((hipfire: Float, aimSight: Float, confidence: Int) -> Unit)? = null

    companion object {
        private const val KEY_DATA = "realtime_data"
        // 偏差阈值（度）：超过这个才认为"打偏了"
        private const val DEVIATION_THRESHOLD = 15f
        // 过冲阈值（像素）：超过这个才认为"冲过头了"
        private const val OVERSHOOT_THRESHOLD = 8f
    }

    // ==================== 笔画采集 ====================

    /** 手指按下时调用 */
    fun beginStroke(x: Float, y: Float) {
        strokeSamples.clear()
        strokeStartTime = System.currentTimeMillis()
        strokeStartX = x
        strokeStartY = y
        strokeSamples.add(StrokeSample(x, y, strokeStartTime))
    }

    /** 手指移动时每帧调用 */
    fun addSample(x: Float, y: Float) {
        strokeSamples.add(StrokeSample(x, y, System.currentTimeMillis()))
    }

    /** 手指抬起时调用，完成一次瞄准分析 */
    fun endStroke(endX: Float, endY: Float) {
        strokeSamples.add(StrokeSample(endX, endY, System.currentTimeMillis()))

        val duration = System.currentTimeMillis() - strokeStartTime
        // 过滤：太短（<30ms）或距离太小（<5px）的忽略，不是瞄准动作
        val totalDist = distance(strokeStartX, strokeStartY, endX, endY)
        if (duration < 30 || totalDist < 5f || strokeSamples.size < 3) return

        val point = analyzeStroke() ?: return

        // 加入对应窗口
        val window = if (point.wasAiming) aimSightWindow else hipfireWindow
        window.addLast(point)
        if (window.size > WINDOW_SIZE) window.removeFirst()

        // 实时重算推荐值
        recalculate()
    }

    // ==================== 单次笔画分析 ====================

    private fun analyzeStroke(): TouchDataPoint? {
        if (strokeSamples.size < 3) return null

        val config = SensitivityEngine.config
        val endX = strokeSamples.last().x
        val endY = strokeSamples.last().y

        // --- 速度计算 ---
        val speeds = mutableListOf<Float>()
        for (i in 1 until strokeSamples.size) {
            val dt = (strokeSamples[i].time - strokeSamples[i-1].time).toFloat()
            if (dt <= 0f) continue
            val dx = strokeSamples[i].x - strokeSamples[i-1].x
            val dy = strokeSamples[i].y - strokeSamples[i-1].y
            speeds.add(sqrt(dx*dx + dy*dy) / dt)
        }
        if (speeds.isEmpty()) return null

        val avgSpeed = speeds.average().toFloat()
        val peakSpeed = speeds.max()
        val totalDist = (1 until strokeSamples.size).sumOf { i ->
            distance(strokeSamples[i-1].x, strokeSamples[i-1].y,
                     strokeSamples[i].x, strokeSamples[i].y).toDouble()
        }.toFloat()

        // 速度方差（一致性）
        val variance = speeds.map { (it - avgSpeed).pow(2) }.average().toFloat()
        val consistency = (1f - (sqrt(variance) / (avgSpeed + 0.001f)).coerceIn(0f, 1f))

        // 减速比：后半段 vs 前半段平均速度
        val half = speeds.size / 2
        val firstHalfAvg = if (half > 0) speeds.take(half).average().toFloat() else avgSpeed
        val secondHalfAvg = if (half > 0) speeds.drop(half).average().toFloat() else avgSpeed
        val decelerationRatio = secondHalfAvg / (firstHalfAvg + 0.001f)

        // --- 偏差计算 ---
        // 意图方向：起点 → 终点的直线角度
        val intentAngle = atan2(endY - strokeStartY, endX - strokeStartX)

        // 每个中间点相对于"意图直线"的偏差角度
        var totalDeviation = 0f
        var maxDeviation = 0f
        for (sample in strokeSamples.drop(1).dropLast(1)) {
            val sampleAngle = atan2(sample.y - strokeStartY, sample.x - strokeStartX)
            val diff = abs(normalizeAngle(sampleAngle - intentAngle)) * (180f / PI.toFloat())
            totalDeviation += diff
            if (diff > maxDeviation) maxDeviation = diff
        }
        val avgDeviation = totalDeviation / (strokeSamples.size - 2).coerceAtLeast(1)

        // --- 过冲计算 ---
        // 思路：把轨迹投影到意图方向上，
        // 中途最大投影距离 vs 终点投影距离的差 = 过冲
        val intentDx = cos(intentAngle)
        val intentDy = sin(intentAngle)
        var maxProjection = 0f
        for (sample in strokeSamples) {
            val proj = (sample.x - strokeStartX) * intentDx +
                       (sample.y - strokeStartY) * intentDy
            if (proj > maxProjection) maxProjection = proj
        }
        val finalProjection = (endX - strokeStartX) * intentDx +
                              (endY - strokeStartY) * intentDy
        val overshoot = maxProjection - finalProjection  // 正=冲过头后回来了

        return TouchDataPoint(
            peakSpeedPxPerMs = peakSpeed,
            avgSpeedPxPerMs = avgSpeed,
            totalDistancePx = totalDist,
            durationMs = (System.currentTimeMillis() - strokeStartTime),
            intentAngleDeg = intentAngle * (180f / PI.toFloat()),
            avgDeviationDeg = avgDeviation,
            maxDeviationDeg = maxDeviation,
            overshootPx = overshoot,
            decelerationRatio = decelerationRatio,
            speedConsistency = consistency,
            hipfireSens = config.hipfireSens,
            aimSightSens = config.aimSightSens,
            wasAiming = SensitivityEngine.isAiming
        )
    }


    /**
     * 陀螺仪数据记录（由 SensitivityAccessibilityService 调用）
     * @param samples 一段连续的陀螺仪采样（旋转速率 rad/s）
     * @param durationMs 采样总时长
     */
    fun recordGyroStroke(samples: List<Float>, durationMs: Long) {
        if (samples.size < 3) return
        val config = SensitivityEngine.config
        if (!config.gyroEnabled) return

        val avg     = samples.average().toFloat()
        val peak    = samples.map { Math.abs(it) }.max()
        val endRate = Math.abs(samples.last())
        val variance = samples.map { (it - avg) * (it - avg) }.average().toFloat()

        val point = GyroDataPoint(
            avgRotationRate  = avg,
            peakRotationRate = peak,
            durationMs       = durationMs,
            endRotationRate  = endRate,
            rotationVariance = variance,
            gyroSens         = config.gyroSens,
            wasAiming        = SensitivityEngine.isAiming
        )

        gyroWindow.addLast(point)
        if (gyroWindow.size > WINDOW_SIZE) gyroWindow.removeFirst()
        recalculate()
    }

    // ==================== 实时推荐值计算 ====================

    private fun recalculate() {
        val hipfireStats  = calcStats(hipfireWindow)
        val aimSightStats = calcStats(aimSightWindow)
        val gyroStats     = calcGyroStats(gyroWindow)

        realtimeStats = RealtimeStats(
            hipfire      = hipfireStats,
            aimSight     = aimSightStats,
            gyro         = gyroStats,
            lastUpdated  = System.currentTimeMillis(),
            totalSamples = hipfireWindow.size + aimSightWindow.size
        )

        // 置信度：样本越多越高，最高100%
        val confidence = ((hipfireWindow.size + aimSightWindow.size) / 20f * 100)
            .toInt().coerceIn(5, 100)

        // 计算推荐值
        val currentConfig = SensitivityEngine.config
        val recHipfire = calcRecommendedValue(
            currentValue = currentConfig.hipfireSens,
            stats = hipfireStats,
            minVal = 0.1f,
            maxVal = 3.0f
        )
        val recAimSight = calcRecommendedValue(
            currentValue = currentConfig.aimSightSens,
            stats = aimSightStats,
            minVal = 0.1f,
            maxVal = 2.0f
        )

        // 保存到本地
        saveRecommendation(recHipfire, recAimSight, confidence)

        // 通知悬浮窗更新
        onRecommendationUpdated?.invoke(recHipfire, recAimSight, confidence)
    }

    private fun calcStats(window: ArrayDeque<TouchDataPoint>): SensStats {
        if (window.isEmpty()) return SensStats()

        val recent = window.toList()
        val avgDev = recent.map { it.avgDeviationDeg }.average().toFloat()
        val avgOvershoot = recent.map { it.overshootPx }.average().toFloat()
        val avgSpeed = recent.map { it.avgSpeedPxPerMs }.average().toFloat()
        val speedVar = recent.map { (it.avgSpeedPxPerMs - avgSpeed).pow(2) }.average().toFloat()

        // 判断需要调整的方向和幅度
        // 过冲 > 阈值 → 灵敏度偏高，需要调低
        // 过冲 < -阈值 → 灵敏度偏低，需要调高
        // 偏差 > 阈值 → 也可能偏高（轨迹不稳）
        val overshootSignal = avgOvershoot / OVERSHOOT_THRESHOLD  // 归一化
        val deviationSignal = (avgDev - DEVIATION_THRESHOLD) / DEVIATION_THRESHOLD

        val combinedSignal = overshootSignal * 0.6f + deviationSignal * 0.4f

        val adjustDirection = when {
            combinedSignal > 0.3f -> -1   // 明显过冲/偏差 → 调低
            combinedSignal < -0.3f -> +1  // 没冲到/偏差小 → 可以调高
            else -> 0                     // 刚好
        }

        // 调整幅度：信号越强调整越多，最多0.4
        val adjustAmount = (abs(combinedSignal) * 0.3f).coerceIn(0f, 0.4f)

        return SensStats(
            sampleCount = recent.size,
            avgDeviationDeg = avgDev,
            overshootRate = avgOvershoot,
            avgSpeed = avgSpeed,
            speedVariance = speedVar,
            adjustDirection = adjustDirection,
            adjustAmount = adjustAmount
        )
    }

    private fun calcRecommendedValue(
        currentValue: Float,
        stats: SensStats,
        minVal: Float,
        maxVal: Float
    ): Float {
        if (stats.sampleCount < 5) return currentValue  // 样本不足，不调整

        val delta = stats.adjustDirection * stats.adjustAmount
        return (currentValue + delta)
            .coerceIn(minVal, maxVal)
            .roundToOneDecimal()
    }

    private fun calcGyroStats(window: ArrayDeque<GyroDataPoint>): GyroStats {
        if (window.size < 3) return GyroStats()

        val recent          = window.toList()
        val avgRate         = recent.map { Math.abs(it.avgRotationRate) }.average().toFloat()
        val avgEndStability = recent.map { it.endRotationRate }.average().toFloat()
        val avgVariance     = recent.map { it.rotationVariance }.average().toFloat()

        // 判断手腕是否稳定：结束时速率低 + 方差小 = 稳定
        val isStable = avgEndStability < 0.3f && avgVariance < 0.05f

        // 推荐强度：手速越快 → 强度适当低一些；手腕越稳 → 可以高一些
        val baseRecommend = when {
            avgRate > 2.0f -> 0.5f   // 手腕动作幅度大 → 低强度
            avgRate > 1.0f -> 0.8f   // 正常
            else           -> 1.2f   // 动作幅度小 → 可提高强度
        }
        val stabilityBonus = if (isStable) 0.1f else -0.1f
        val recommended = (baseRecommend + stabilityBonus).coerceIn(0.2f, 2.0f).roundToOneDecimal()

        return GyroStats(
            sampleCount      = recent.size,
            avgRotationRate  = avgRate,
            avgEndStability  = avgEndStability,
            avgVariance      = avgVariance,
            isStableEnough   = isStable,
            recommendedSens  = recommended
        )
    }

    // ==================== 持久化 ====================

    private fun saveRecommendation(hipfire: Float, aimSight: Float, confidence: Int) {
        prefs.edit()
            .putFloat("rec_hipfire", hipfire)
            .putFloat("rec_aimsight", aimSight)
            .putInt("rec_confidence", confidence)
            .putLong("rec_time", System.currentTimeMillis())
            .apply()
    }

    fun loadLastRecommendation(): Triple<Float, Float, Int>? {
        val hipfire = prefs.getFloat("rec_hipfire", -1f)
        if (hipfire < 0) return null
        return Triple(
            hipfire,
            prefs.getFloat("rec_aimsight", SensitivityEngine.config.aimSightSens),
            prefs.getInt("rec_confidence", 0)
        )
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        hipfireWindow.clear()
        aimSightWindow.clear()
        gyroWindow.clear()
        realtimeStats = RealtimeStats()
    }

    // ==================== 工具 ====================

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a > PI) a -= (2 * PI).toFloat()
        while (a < -PI) a += (2 * PI).toFloat()
        return a
    }

    private fun Float.roundToOneDecimal(): Float =
        (this * 10).toInt() / 10f

    private data class StrokeSample(val x: Float, val y: Float, val time: Long)

    // 为了省内存，旧版 SessionSummary 接口兼容（空实现）
    fun loadAllSessions(): List<Any> = emptyList()
}
