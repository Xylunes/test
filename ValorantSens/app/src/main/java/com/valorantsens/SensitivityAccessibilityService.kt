package com.valorantsens

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：拦截触控事件并应用灵敏度
 * 同时处理陀螺仪数据
 */
class SensitivityAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var lastGyroTime: Long = 0L

    // 陀螺仪采样缓冲（收集一段连续采样后一起送给 DataCollector）
    private val gyroSamples = mutableListOf<Float>()
    private var gyroStrokeStart = 0L
    private val GYRO_IDLE_MS = 200L  // 超过200ms无动作，视为一次陀螺仪操作结束

    // 屏幕尺寸
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // 视角区域左边界（屏幕45%处）
    private val aimZoneRatioX = 0.45f

    // 开镜区域（右下角）
    private val aimButtonZoneX = 0.75f
    private val aimButtonZoneY = 0.65f

    // 追踪每个触控指针的上一个位置
    private val lastPointerX = HashMap<Int, Float>()
    private val lastPointerY = HashMap<Int, Float>()

    companion object {
        // 供其他组件判断服务是否运行
        var isRunning = false
    }

    override fun onServiceConnected() {
        isRunning = true

        // 获取屏幕尺寸
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        // 初始化陀螺仪
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 暂时不需要处理UI事件，触控拦截通过 onKeyEvent / gesture 实现
        // 后续可在此检测开镜状态
    }

    override fun onInterrupt() {
        // 服务被中断时调用
    }

    override fun onDestroy() {
        isRunning = false
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    // ==================== 陀螺仪处理 ====================

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (!SensitivityEngine.config.gyroEnabled) return

        val now = System.currentTimeMillis()
        val deltaTime = if (lastGyroTime == 0L) 16f else (now - lastGyroTime).toFloat()
        lastGyroTime = now

        // event.values[1] = 绕Y轴旋转（偏航，控制左右视角）
        val deltaX = SensitivityEngine.applyGyro(-event.values[1], deltaTime)
        val deltaY = SensitivityEngine.applyGyro(-event.values[0], deltaTime)

        if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
            injectGyroGesture(deltaX, deltaY)
        }

        // 采集陀螺仪数据供 AI 分析
        val rotRate = Math.sqrt(
            (event.values[0] * event.values[0] +
             event.values[1] * event.values[1]).toDouble()
        ).toFloat()

        if (rotRate > 0.05f) {
            // 有旋转：加入缓冲
            if (gyroSamples.isEmpty()) gyroStrokeStart = now
            gyroSamples.add(rotRate)
        } else if (gyroSamples.size >= 3 &&
                   (now - lastGyroTime) > GYRO_IDLE_MS) {
            // 停止旋转超过阈值：提交本次陀螺仪操作
            val duration = now - gyroStrokeStart
            globalDataCollector.recordGyroStroke(gyroSamples.toList(), duration)
            gyroSamples.clear()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 将陀螺仪偏移注入为手势
     * 在屏幕视角区域中心模拟微小滑动
     */
    private fun injectGyroGesture(deltaX: Float, deltaY: Float) {
        val centerX = screenWidth * 0.72f  // 视角区中心
        val centerY = screenHeight * 0.5f

        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + deltaX, centerY + deltaY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 16))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * 判断触控点是否在视角区域内
     */
    private fun isInAimZone(x: Float): Boolean {
        return x > screenWidth * aimZoneRatioX
    }

    /**
     * 判断触控点是否在开镜按钮区域（用于自动检测开镜状态）
     */
    private fun isInAimButtonZone(x: Float, y: Float): Boolean {
        return x > screenWidth * aimButtonZoneX &&
               y > screenHeight * aimButtonZoneY
    }
}
