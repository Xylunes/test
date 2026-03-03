package com.valorantsens

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AiAnalysisActivity : AppCompatActivity() {

    private lateinit var client: DeepSeekClient

    private lateinit var etApiKey:    EditText
    private lateinit var btnSaveKey:  Button
    private lateinit var tvKeyStatus: TextView
    private lateinit var btnAnalyze:  Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSamples:   TextView

    // 结果
    private lateinit var layoutResult:    LinearLayout
    private lateinit var tvSlideOverall:  TextView
    private lateinit var tvSlideVertical: TextView
    private lateinit var tvGyroMode:      TextView
    private lateinit var tvGyroOverall:   TextView
    private lateinit var tvGyroVertical:  TextView
    private lateinit var layoutGyroResult: LinearLayout
    private lateinit var btnApply:        Button

    private lateinit var tvError: TextView
    private var lastResult: DeepSeekResult.Success? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_analysis)
        client = DeepSeekClient(this)
        initViews()
        loadSavedKey()
        updateSampleCount()
    }

    private fun initViews() {
        findViewById<Button>(R.id.btn_back_ai).setOnClickListener { finish() }

        etApiKey     = findViewById(R.id.et_api_key)
        btnSaveKey   = findViewById(R.id.btn_save_key)
        tvKeyStatus  = findViewById(R.id.tv_key_status)
        btnAnalyze   = findViewById(R.id.btn_analyze)
        progressBar  = findViewById(R.id.progress_analyzing)
        tvSamples    = findViewById(R.id.tv_samples)
        layoutResult = findViewById(R.id.layout_result)
        tvSlideOverall  = findViewById(R.id.tv_slide_overall_result)
        tvSlideVertical = findViewById(R.id.tv_slide_vertical_result)
        tvGyroMode      = findViewById(R.id.tv_gyro_mode_result)
        tvGyroOverall   = findViewById(R.id.tv_gyro_overall_result)
        tvGyroVertical  = findViewById(R.id.tv_gyro_vertical_result)
        layoutGyroResult = findViewById(R.id.layout_gyro_result)
        btnApply     = findViewById(R.id.btn_apply_ai)
        tvError      = findViewById(R.id.tv_error)

        btnSaveKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.length < 20) { showError("Key 格式不对，请重新粘贴"); return@setOnClickListener }
            client.saveApiKey(key)
            tvKeyStatus.text = "✅ 已保存"
            tvKeyStatus.setTextColor(0xFF00BF63.toInt())
            hideKeyboard()
        }
        btnAnalyze.setOnClickListener { startAnalysis() }
        btnApply.setOnClickListener { applyResult() }

        layoutResult.visibility = View.GONE
        tvError.visibility      = View.GONE
        progressBar.visibility  = View.GONE
    }

    private fun loadSavedKey() {
        val key = client.getApiKey()
        if (key.isNotBlank()) {
            etApiKey.setText("sk-••••••" + key.takeLast(6))
            tvKeyStatus.text = "✅ 已保存"
            tvKeyStatus.setTextColor(0xFF00BF63.toInt())
        }
    }

    private fun updateSampleCount() {
        val n = globalDataCollector.realtimeStats.totalSamples
        tvSamples.text = "已采集 $n 次瞄准数据${if (n < 5) "（至少需要 5 次）" else ""}"
    }

    private fun startAnalysis() {
        if (!client.hasApiKey()) { showError("请先填写并保存 API Key"); return }
        tvError.visibility      = View.GONE
        layoutResult.visibility = View.GONE
        btnAnalyze.isEnabled    = false
        progressBar.visibility  = View.VISIBLE

        lifecycleScope.launch {
            val result = client.analyze(
                globalDataCollector.realtimeStats,
                SensitivityConfig.load(this@AiAnalysisActivity)
            )
            progressBar.visibility = View.GONE
            btnAnalyze.isEnabled   = true
            when (result) {
                is DeepSeekResult.Success -> showResult(result)
                is DeepSeekResult.Error   -> showError(result.message)
            }
        }
    }

    private fun showResult(r: DeepSeekResult.Success) {
        lastResult = r
        tvError.visibility      = View.GONE
        layoutResult.visibility = View.VISIBLE

        val cfg = SensitivityConfig.load(this)

        // 滑屏整体
        tvSlideOverall.text = formatIntChange(cfg.slideOverall, r.slideOverall, "%")
        tvSlideOverall.setTextColor(changeColor(cfg.slideOverall, r.slideOverall))

        // 滑屏Y轴
        tvSlideVertical.text = formatIntChange(cfg.slideVertical, r.slideVertical, "%")
        tvSlideVertical.setTextColor(changeColor(cfg.slideVertical, r.slideVertical))

        // 陀螺仪模式
        val modeLabel = mapOf(
            SensitivityConfig.GyroMode.OFF      to "关闭",
            SensitivityConfig.GyroMode.AIM_ONLY to "开镜开启",
            SensitivityConfig.GyroMode.ALWAYS   to "总是开启"
        )
        val modeChanged = r.gyroMode != cfg.gyroMode
        tvGyroMode.text = if (modeChanged)
            "${modeLabel[cfg.gyroMode]}  →  ${modeLabel[r.gyroMode]}"
        else "${modeLabel[r.gyroMode]}  ✅"
        tvGyroMode.setTextColor(if (modeChanged) 0xFFFFB347.toInt() else 0xFF00BF63.toInt())

        // 陀螺仪灵敏度（仅开启时显示）
        layoutGyroResult.visibility =
            if (r.gyroMode != SensitivityConfig.GyroMode.OFF) View.VISIBLE else View.GONE

        tvGyroOverall.text = formatIntChange(cfg.gyroOverall, r.gyroOverall, "%")
        tvGyroOverall.setTextColor(changeColor(cfg.gyroOverall, r.gyroOverall))

        tvGyroVertical.text = formatIntChange(cfg.gyroVertical, r.gyroVertical, "%")
        tvGyroVertical.setTextColor(changeColor(cfg.gyroVertical, r.gyroVertical))

        btnApply.isEnabled = true
        btnApply.alpha     = 1f
    }

    private fun applyResult() {
        val r = lastResult ?: return
        val newConfig = SensitivityConfig(
            slideOverall  = r.slideOverall,
            slideVertical = r.slideVertical,
            gyroMode      = r.gyroMode,
            gyroOverall   = r.gyroOverall,
            gyroVertical  = r.gyroVertical
        )
        newConfig.save(this)
        SensitivityEngine.config = newConfig
        Toast.makeText(this, "✅ AI 推荐已应用", Toast.LENGTH_SHORT).show()
        btnApply.text      = "✅ 已应用"
        btnApply.isEnabled = false
    }

    private fun formatIntChange(current: Int, recommended: Int, suffix: String): String =
        if (current == recommended) "$recommended$suffix  ✅"
        else "$current$suffix  →  $recommended$suffix"

    private fun changeColor(current: Int, recommended: Int): Int =
        if (current == recommended) 0xFF00BF63.toInt() else 0xFFFFB347.toInt()

    private fun showError(msg: String) {
        tvError.text       = "⚠️ $msg"
        tvError.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etApiKey.windowToken, 0)
    }
}
