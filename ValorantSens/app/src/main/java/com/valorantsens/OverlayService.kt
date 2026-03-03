package com.valorantsens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat

/**
 * 悬浮窗服务
 * 在游戏上层显示可拖动的悬浮球，点击展开调节面板
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    // 悬浮球视图
    private var floatBallView: View? = null
    private lateinit var floatBallParams: WindowManager.LayoutParams

    // 调节面板视图
    private var controlPanelView: View? = null
    private var isPanelVisible = false

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        const val NOTIF_CHANNEL_ID = "overlay_channel"
        const val NOTIF_ID = 1001
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        showFloatBall()

        // 开始本局数据采集
        globalDataCollector.startSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        floatBallView?.let { windowManager.removeView(it) }
        controlPanelView?.let { windowManager.removeView(it) }

        super.onDestroy()
    }

    // ==================== 前台通知 ====================

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "无畏契约灵敏度调节",
            NotificationManager.IMPORTANCE_LOW
        )
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("灵敏度调节运行中")
            .setContentText("点击返回设置界面")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    // ==================== 悬浮球 ====================

    private fun showFloatBall() {
        floatBallParams = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        floatBallView = LayoutInflater.from(this).inflate(R.layout.view_float_ball, null)

        floatBallView?.setOnTouchListener(object : View.OnTouchListener {
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        initialX = floatBallParams.x
                        initialY = floatBallParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        // 开始记录一次瞄准笔画
                        globalDataCollector.beginStroke(event.rawX, event.rawY)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) isDragging = true
                        if (isDragging) {
                            floatBallParams.x = (initialX + dx).toInt()
                            floatBallParams.y = (initialY + dy).toInt()
                            windowManager.updateViewLayout(floatBallView, floatBallParams)
                        } else {
                            // 视角滑动：逐帧记录轨迹
                            globalDataCollector.addSample(event.rawX, event.rawY)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 完成笔画，触发偏差分析
                        globalDataCollector.endStroke(event.rawX, event.rawY)
                        if (!isDragging) toggleControlPanel()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatBallView, floatBallParams)
    }

    // ==================== 调节面板 ====================

    private fun toggleControlPanel() {
        if (isPanelVisible) {
            hideControlPanel()
        } else {
            showControlPanel()
        }
    }

    private fun showControlPanel() {
        if (controlPanelView != null) return

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 显示在悬浮球右边
            x = floatBallParams.x + 130
            y = floatBallParams.y
        }

        controlPanelView = LayoutInflater.from(this).inflate(R.layout.view_control_panel, null)

        // 关闭按钮
        controlPanelView?.findViewById<ImageButton>(R.id.btn_close_panel)?.setOnClickListener {
            hideControlPanel()
        }

        // 绑定滑块控件（由 ControlPanelController 处理）
        controlPanelView?.let {
            ControlPanelController(this, it).setup()
        }

        windowManager.addView(controlPanelView, panelParams)
        isPanelVisible = true
    }

    private fun hideControlPanel() {
        controlPanelView?.let {
            windowManager.removeView(it)
            controlPanelView = null
        }
        isPanelVisible = false
    }
}
