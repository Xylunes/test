package com.valorantsens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

lateinit var globalDataCollector: DataCollector

class MainActivity : AppCompatActivity() {

    private lateinit var config: SensitivityConfig

    // 滑屏控件
    private lateinit var sbSlideOverall:  SeekBar
    private lateinit var tvSlideOverall:  TextView
    private lateinit var sbSlideVertical: SeekBar
    private lateinit var tvSlideVertical: TextView

    // 陀螺仪控件
    private lateinit var btnGyroOff:    Button
    private lateinit var btnGyroAim:    Button
    private lateinit var btnGyroAlways: Button
    private lateinit var layoutGyroSliders: LinearLayout
    private lateinit var sbGyroOverall:  SeekBar
    private lateinit var tvGyroOverall:  TextView
    private lateinit var sbGyroVertical: SeekBar
    private lateinit var tvGyroVertical: TextView

    // 其他
    private lateinit var btnToggleService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = SensitivityConfig.load(this)
        SensitivityEngine.config = config
        globalDataCollector = DataCollector(this)

        initViews()
        loadConfigToUI()
        setupSliders()
        setupGyroButtons()
        setupPresets()
        setupServiceButton()
        setupAnalysisButtons()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    // ==================== 初始化控件 ====================

    private fun initViews() {
        sbSlideOverall   = findViewById(R.id.sb_slide_overall)
        tvSlideOverall   = findViewById(R.id.tv_slide_overall_val)
        sbSlideVertical  = findViewById(R.id.sb_slide_vertical)
        tvSlideVertical  = findViewById(R.id.tv_slide_vertical_val)

        btnGyroOff       = findViewById(R.id.btn_gyro_off)
        btnGyroAim       = findViewById(R.id.btn_gyro_aim)
        btnGyroAlways    = findViewById(R.id.btn_gyro_always)
        layoutGyroSliders= findViewById(R.id.layout_gyro_sliders)
        sbGyroOverall    = findViewById(R.id.sb_gyro_overall)
        tvGyroOverall    = findViewById(R.id.tv_gyro_overall_val)
        sbGyroVertical   = findViewById(R.id.sb_gyro_vertical)
        tvGyroVertical   = findViewById(R.id.tv_gyro_vertical_val)

        btnToggleService = findViewById(R.id.btn_toggle_service)
    }

    // ==================== 从配置恢复 UI ====================

    private fun loadConfigToUI() {
        // 滑屏
        sbSlideOverall.progress  = config.slideOverall - 1
        tvSlideOverall.text      = "${config.slideOverall}%"
        sbSlideVertical.progress = config.slideVertical - 1
        tvSlideVertical.text     = "${config.slideVertical}%"

        // 陀螺仪
        sbGyroOverall.progress   = config.gyroOverall - 1
        tvGyroOverall.text       = "${config.gyroOverall}%"
        sbGyroVertical.progress  = config.gyroVertical - 1
        tvGyroVertical.text      = "${config.gyroVertical}%"

        updateGyroModeButtons(config.gyroMode)
    }

    // ==================== 滑块绑定 ====================

    private fun setupSliders() {
        sbSlideOverall.setOnSeekBarChangeListener(simpleSeekListener { v ->
            val pct = v + 1
            tvSlideOverall.text = "$pct%"
            saveConfig { config.copy(slideOverall = pct) }
        })
        sbSlideVertical.setOnSeekBarChangeListener(simpleSeekListener { v ->
            val pct = v + 1
            tvSlideVertical.text = "$pct%"
            saveConfig { config.copy(slideVertical = pct) }
        })
        sbGyroOverall.setOnSeekBarChangeListener(simpleSeekListener { v ->
            val pct = v + 1
            tvGyroOverall.text = "$pct%"
            saveConfig { config.copy(gyroOverall = pct) }
        })
        sbGyroVertical.setOnSeekBarChangeListener(simpleSeekListener { v ->
            val pct = v + 1
            tvGyroVertical.text = "$pct%"
            saveConfig { config.copy(gyroVertical = pct) }
        })
    }

    // ==================== 陀螺仪模式按钮 ====================

    private fun setupGyroButtons() {
        btnGyroOff.setOnClickListener    { setGyroMode(SensitivityConfig.GyroMode.OFF) }
        btnGyroAim.setOnClickListener    { setGyroMode(SensitivityConfig.GyroMode.AIM_ONLY) }
        btnGyroAlways.setOnClickListener { setGyroMode(SensitivityConfig.GyroMode.ALWAYS) }
    }

    private fun setGyroMode(mode: SensitivityConfig.GyroMode) {
        saveConfig { config.copy(gyroMode = mode) }
        updateGyroModeButtons(mode)
    }

    private fun updateGyroModeButtons(mode: SensitivityConfig.GyroMode) {
        val activeColor   = 0xFFFF4655.toInt()
        val inactiveColor = 0xFF2A2A4A.toInt()
        val activeText    = 0xFFFFFFFF.toInt()
        val inactiveText  = 0xFFAAAAAA.toInt()

        listOf(
            btnGyroOff    to (mode == SensitivityConfig.GyroMode.OFF),
            btnGyroAim    to (mode == SensitivityConfig.GyroMode.AIM_ONLY),
            btnGyroAlways to (mode == SensitivityConfig.GyroMode.ALWAYS)
        ).forEach { (btn, active) ->
            btn.setBackgroundColor(if (active) activeColor else inactiveColor)
            btn.setTextColor(if (active) activeText else inactiveText)
        }

        // 陀螺仪关闭时隐藏灵敏度滑块
        layoutGyroSliders.visibility =
            if (mode == SensitivityConfig.GyroMode.OFF) View.GONE else View.VISIBLE
    }

    // ==================== 预设方案 ====================

    private fun setupPresets() {
        // 低：整体慢，适合新手精准
        findViewById<Button>(R.id.btn_preset_low).setOnClickListener {
            applyPreset(slideOverall=80, slideVertical=20, gyroOverall=100, gyroVertical=40)
        }
        // 中：均衡，游戏推荐附近
        findViewById<Button>(R.id.btn_preset_mid).setOnClickListener {
            applyPreset(slideOverall=114, slideVertical=28, gyroOverall=172, gyroVertical=49)
        }
        // 高：快速，适合老手
        findViewById<Button>(R.id.btn_preset_high).setOnClickListener {
            applyPreset(slideOverall=150, slideVertical=40, gyroOverall=220, gyroVertical=65)
        }
    }

    private fun applyPreset(slideOverall: Int, slideVertical: Int,
                            gyroOverall: Int, gyroVertical: Int) {
        saveConfig {
            config.copy(
                slideOverall  = slideOverall,
                slideVertical = slideVertical,
                gyroOverall   = gyroOverall,
                gyroVertical  = gyroVertical
            )
        }
        loadConfigToUI()
        Toast.makeText(this, "预设已应用", Toast.LENGTH_SHORT).show()
    }

    // ==================== 启动/停止服务 ====================

    private fun setupServiceButton() {
        btnToggleService.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener

            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
                btnToggleService.text = "▶ 启动悬浮球"
                btnToggleService.setBackgroundColor(0xFF00BF63.toInt())
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                btnToggleService.text = "■ 停止"
                btnToggleService.setBackgroundColor(0xFFFF4655.toInt())
            }
        }
    }

    // ==================== 分析入口 ====================

    private fun setupAnalysisButtons() {
        val n = globalDataCollector.realtimeStats.totalSamples
        findViewById<TextView>(R.id.tv_session_count_hint)?.text =
            if (n > 0) "已采集 $n 次瞄准数据" else "开启悬浮球打游戏后自动采集数据"

        findViewById<Button>(R.id.btn_open_analysis)?.setOnClickListener {
            startActivity(Intent(this, AnalysisActivity::class.java))
        }
        findViewById<Button>(R.id.btn_open_ai_analysis)?.setOnClickListener {
            startActivity(Intent(this, AiAnalysisActivity::class.java))
        }
    }

    // ==================== 权限检测 ====================

    private fun checkPermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun updatePermissionStatus() {
        val overlayOk       = Settings.canDrawOverlays(this)
        val tvOverlay       = findViewById<TextView>(R.id.tv_overlay_status)
        val tvAccessibility = findViewById<TextView>(R.id.tv_accessibility_status)
        val btnPerm         = findViewById<Button>(R.id.btn_request_permissions)

        tvOverlay.text      = if (overlayOk) "✅ 悬浮窗权限" else "❌ 悬浮窗权限（未授权）"
        tvOverlay.setTextColor(if (overlayOk) 0xFF00BF63.toInt() else 0xFFFF6B6B.toInt())

        // 无障碍服务状态（简单检测）
        val a11yEnabled = isAccessibilityEnabled()
        tvAccessibility.text = if (a11yEnabled) "✅ 无障碍服务" else "❌ 无障碍服务（未开启）"
        tvAccessibility.setTextColor(if (a11yEnabled) 0xFF00BF63.toInt() else 0xFFFF6B6B.toInt())

        btnPerm.visibility = if (!overlayOk || !a11yEnabled) View.VISIBLE else View.GONE
        btnPerm.setOnClickListener {
            if (!overlayOk) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnToggleService.isEnabled = overlayOk
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(packageName)
        } catch (e: Exception) { false }
    }

    // ==================== 工具 ====================

    private fun saveConfig(update: () -> SensitivityConfig) {
        config = update()
        config.save(this)
        SensitivityEngine.config = config
    }

    private fun simpleSeekListener(onProgress: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                if (fromUser) onProgress(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }
}
