package com.valorantsens

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 灵敏度推荐引擎（精简版）
 *
 * 职责：接收 DataCollector 的实时统计，生成人类可读的推荐结果
 * 不做图表，直接输出：推荐数值 + 一句话原因
 */
object SensitivityAnalyzer {

    /**
     * 推荐结果（直接显示给用户）
     */
    data class Recommendation(
        // 推荐的灵敏度数值
        val hipfire: Float,
        val aimSight: Float,

        // 置信度（样本越多越高）
        val confidence: Int,        // 0~100

        // 每项的诊断（一句话）
        val hipfireDiagnosis: String,
        val aimSightDiagnosis: String,

        // 是否与当前值不同（不同才提示用户调整）
        val hipfireChanged: Boolean,
        val aimSightChanged: Boolean,

        // 总体状态
        val status: Status
    )

    enum class Status {
        COLLECTING,      // 数据不足，采集中
        OPTIMAL,         // 当前灵敏度已最优
        NEEDS_ADJUST     // 需要调整
    }

    /**
     * 根据实时统计生成推荐
     */
    fun recommend(
        stats: RealtimeStats,
        currentConfig: SensitivityConfig
    ): Recommendation {
        val total = stats.totalSamples

        // 样本太少 → 采集中
        if (total < 5) {
            return Recommendation(
                hipfire = currentConfig.hipfireSens,
                aimSight = currentConfig.aimSightSens,
                confidence = (total / 5f * 30).toInt(),
                hipfireDiagnosis = "采集中，请继续游戏…（$total/5）",
                aimSightDiagnosis = "采集中，请继续游戏…",
                hipfireChanged = false,
                aimSightChanged = false,
                status = Status.COLLECTING
            )
        }

        // 生成腰射推荐
        val (recHipfire, hipfireDiag) = buildRecommendation(
            label = "腰射",
            current = currentConfig.hipfireSens,
            stats = stats.hipfire,
            minVal = 0.1f,
            maxVal = 3.0f
        )

        // 生成开镜推荐
        val (recAimSight, aimSightDiag) = buildRecommendation(
            label = "开镜",
            current = currentConfig.aimSightSens,
            stats = stats.aimSight,
            minVal = 0.1f,
            maxVal = 2.0f
        )

        val hipfireChanged = abs(recHipfire - currentConfig.hipfireSens) >= 0.05f
        val aimSightChanged = abs(recAimSight - currentConfig.aimSightSens) >= 0.05f

        val status = if (!hipfireChanged && !aimSightChanged) Status.OPTIMAL else Status.NEEDS_ADJUST

        // 置信度：20样本=60%，50样本=100%
        val confidence = ((total / 50f) * 100).toInt().coerceIn(30, 100)

        return Recommendation(
            hipfire = recHipfire,
            aimSight = recAimSight,
            confidence = confidence,
            hipfireDiagnosis = hipfireDiag,
            aimSightDiagnosis = aimSightDiag,
            hipfireChanged = hipfireChanged,
            aimSightChanged = aimSightChanged,
            status = status
        )
    }

    /**
     * 为单个参数生成推荐值和诊断文字
     */
    private fun buildRecommendation(
        label: String,
        current: Float,
        stats: SensStats,
        minVal: Float,
        maxVal: Float
    ): Pair<Float, String> {
        // 样本不足
        if (stats.sampleCount < 3) {
            return current to "${label}：数据不足（${stats.sampleCount}/3次）"
        }

        val recommended = (current + stats.adjustDirection * stats.adjustAmount)
            .coerceIn(minVal, maxVal)
            .roundToOneDecimal()

        val diagnosis = when {
            // 需要调低
            stats.adjustDirection == -1 -> {
                val reason = when {
                    stats.overshootRate > 12f -> "经常冲过目标 ${stats.overshootRate.toInt()}px"
                    stats.avgDeviationDeg > 20f -> "轨迹偏差 ${stats.avgDeviationDeg.toInt()}°"
                    else -> "手速偏快"
                }
                "$label：偏高（$reason），建议 $current → $recommended"
            }
            // 需要调高
            stats.adjustDirection == 1 -> {
                "$label：偏低（视角移动不够快），建议 $current → $recommended"
            }
            // 刚好
            else -> {
                val devText = "%.0f°".format(stats.avgDeviationDeg)
                "$label：$current ✅（偏差 $devText，表现稳定）"
            }
        }

        return recommended to diagnosis
    }

    private fun Float.roundToOneDecimal(): Float =
        (this * 10).roundToInt() / 10f
}
