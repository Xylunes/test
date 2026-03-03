package com.valorantsens

import kotlin.math.roundToInt

/**
 * 灵敏度引擎 —— 把配置百分比换算成实际像素偏移
 *
 * 游戏参数说明：
 *   slideOverall  = 整体缩放系数（相当于乘以该%）
 *   slideVertical = 在 Overall 基础上额外缩放垂直方向
 *   gyroOverall / gyroVertical 同理，作用于陀螺仪输入
 */
object SensitivityEngine {

    @Volatile var config: SensitivityConfig = SensitivityConfig()
    @Volatile var isAiming: Boolean = false   // 是否处于开镜状态

    /**
     * 处理滑屏输入
     * @param rawDx 原始水平位移（px）
     * @param rawDy 原始垂直位移（px）
     * @return 经灵敏度换算后的 (dx, dy)
     */
    fun applySlide(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val overall  = config.slideOverall  / 100f
        val vertical = config.slideVertical / 100f
        return Pair(rawDx * overall, rawDy * overall * vertical)
    }

    /**
     * 处理陀螺仪输入
     * @param rotX 绕X轴旋转速率 rad/s（控制俯仰，影响 Y 轴）
     * @param rotY 绕Y轴旋转速率 rad/s（控制偏航，影响 X 轴）
     * @param deltaTimeMs 帧时间（毫秒）
     * @return 换算后的像素偏移 (dx, dy)
     */
    fun applyGyro(rotX: Float, rotY: Float, deltaTimeMs: Float): Pair<Float, Float> {
        if (!config.gyroEnabled) return Pair(0f, 0f)
        // 陀螺仪灵敏度：overall 控制整体，vertical 额外控制 Y 轴
        val overall  = config.gyroOverall  / 100f
        val vertical = config.gyroVertical / 100f
        // 基准换算：旋转 1 rad → 约 300px（可根据实际手感调整）
        val baseScale = 300f * (deltaTimeMs / 1000f)
        val dx = -rotY * baseScale * overall
        val dy = -rotX * baseScale * overall * vertical
        return Pair(dx, dy)
    }
}
