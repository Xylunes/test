package com.valorantsens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 灵敏度分析界面（精简版）
 * 只显示：推荐数值 + 诊断原因 + 一键应用
 */
class AnalysisActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvSampleCount: TextView
    private lateinit var progressConfidence: ProgressBar
    private lateinit var tvConfidenceLabel: TextView

    // 腰射卡片
    private lateinit var tvHipfireCurrent: TextView
    private lateinit var tvHipfireArrow: TextView
    private lateinit var tvHipfireRecommended: TextView
    private lateinit var tvHipfireDiagnosis: TextView

    // 开镜卡片
    private lateinit var tvAimSightCurrent: TextView
    private lateinit var tvAimSightArrow: TextView
    private lateinit var tvAimSightRecommended: TextView
    private lateinit var tvAimSightDiagnosis: TextView

    private lateinit var btnApply: Button
    private lateinit var btnClear: Button
    private lateinit var btnBack: Button

    // 自动刷新（每2秒更新一次，因为是实时采集）
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshUI()
            handler.postDelayed(this, 2000)
        }
    }

    private var latestRec: SensitivityAnalyzer.Recommendation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)
        initViews()
        refreshUI()
        handler.post(refreshRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    private fun initViews() {
        tvStatus            = findViewById(R.id.tv_status)
        tvSampleCount       = findViewById(R.id.tv_sample_count)
        progressConfidence  = findViewById(R.id.progress_confidence)
        tvConfidenceLabel   = findViewById(R.id.tv_confidence_label)

        tvHipfireCurrent    = findViewById(R.id.tv_hipfire_current)
        tvHipfireArrow      = findViewById(R.id.tv_hipfire_arrow)
        tvHipfireRecommended= findViewById(R.id.tv_hipfire_recommended)
        tvHipfireDiagnosis  = findViewById(R.id.tv_hipfire_diagnosis)

        tvAimSightCurrent   = findViewById(R.id.tv_aimsight_current)
        tvAimSightArrow     = findViewById(R.id.tv_aimsight_arrow)
        tvAimSightRecommended=findViewById(R.id.tv_aimsight_recommended)
        tvAimSightDiagnosis = findViewById(R.id.tv_aimsight_diagnosis)

        btnApply = findViewById(R.id.btn_apply)
        btnClear = findViewById(R.id.btn_clear)
        btnBack  = findViewById(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        btnApply.setOnClickListener {
            val rec = latestRec ?: return@setOnClickListener
            if (!rec.hipfireChanged && !rec.aimSightChanged) return@setOnClickListener

            val current = SensitivityConfig.load(this)
            val newConfig = current.copy(
                hipfireSens  = rec.hipfire,
                aimSightSens = rec.aimSight
            )
            newConfig.save(this)
            SensitivityEngine.config = newConfig

            Toast.makeText(
                this,
                "✅ 已应用  腰射→${rec.hipfire}  开镜→${rec.aimSight}",
                Toast.LENGTH_LONG
            ).show()
            refreshUI()
        }

        btnClear.setOnClickListener {
            globalDataCollector.clearAllData()
            Toast.makeText(this, "数据已清除，重新开始采集", Toast.LENGTH_SHORT).show()
            refreshUI()
        }
    }

    private fun refreshUI() {
        val config = SensitivityConfig.load(this)
        val stats  = globalDataCollector.realtimeStats
        val rec    = SensitivityAnalyzer.recommend(stats, config)
        latestRec  = rec

        // 状态标题
        tvStatus.text = when (rec.status) {
            SensitivityAnalyzer.Status.COLLECTING   -> "📡 实时采集中…"
            SensitivityAnalyzer.Status.OPTIMAL      -> "✅ 当前灵敏度已最优"
            SensitivityAnalyzer.Status.NEEDS_ADJUST -> "⚡ 发现可优化项"
        }

        // 样本数 & 置信度
        tvSampleCount.text = "已采集 ${stats.totalSamples} 次瞄准动作"
        progressConfidence.progress = rec.confidence
        tvConfidenceLabel.text = "置信度 ${rec.confidence}%"

        // ── 腰射 ──
        tvHipfireCurrent.text = config.hipfireSens.toString()
        tvHipfireRecommended.text = rec.hipfire.toString()
        tvHipfireDiagnosis.text = rec.hipfireDiagnosis

        if (rec.hipfireChanged) {
            tvHipfireArrow.text = if (rec.hipfire > config.hipfireSens) "▲" else "▼"
            tvHipfireArrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            tvHipfireRecommended.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            tvHipfireArrow.text = "="
            tvHipfireArrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvHipfireRecommended.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        }

        // ── 开镜 ──
        tvAimSightCurrent.text = config.aimSightSens.toString()
        tvAimSightRecommended.text = rec.aimSight.toString()
        tvAimSightDiagnosis.text = rec.aimSightDiagnosis

        if (rec.aimSightChanged) {
            tvAimSightArrow.text = if (rec.aimSight > config.aimSightSens) "▲" else "▼"
            tvAimSightArrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            tvAimSightRecommended.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            tvAimSightArrow.text = "="
            tvAimSightArrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvAimSightRecommended.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        }

        // 应用按钮状态
        val canApply = rec.hipfireChanged || rec.aimSightChanged
        btnApply.isEnabled = canApply
        btnApply.alpha = if (canApply) 1f else 0.4f
        btnApply.text = if (canApply) "✨ 应用推荐值" else "✅ 当前已最优，无需调整"
    }
}
