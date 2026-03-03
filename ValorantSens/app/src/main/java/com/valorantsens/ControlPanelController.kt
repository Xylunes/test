package com.valorantsens

import android.content.Context
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView

/**
 * 游戏内悬浮面板控制器
 * 绑定4个滑块：滑屏整体、滑屏Y轴、陀螺仪整体、陀螺仪Y轴
 */
class ControlPanelController(private val context: Context, panelView: android.view.View) {

    init {
        val config = SensitivityConfig.load(context)

        bind(panelView, R.id.sb_panel_slide_overall, R.id.tv_panel_slide_overall,
             config.slideOverall) { v ->
            val pct = v + 1
            updateConfig { it.copy(slideOverall = pct) }
            "$pct%"
        }
        bind(panelView, R.id.sb_panel_slide_vertical, R.id.tv_panel_slide_vertical,
             config.slideVertical) { v ->
            val pct = v + 1
            updateConfig { it.copy(slideVertical = pct) }
            "$pct%"
        }
        bind(panelView, R.id.sb_panel_gyro_overall, R.id.tv_panel_gyro_overall,
             config.gyroOverall) { v ->
            val pct = v + 1
            updateConfig { it.copy(gyroOverall = pct) }
            "$pct%"
        }
        bind(panelView, R.id.sb_panel_gyro_vertical, R.id.tv_panel_gyro_vertical,
             config.gyroVertical) { v ->
            val pct = v + 1
            updateConfig { it.copy(gyroVertical = pct) }
            "$pct%"
        }
    }

    private fun bind(
        view: android.view.View,
        seekId: Int,
        textId: Int,
        currentValue: Int,
        onChanged: (Int) -> String
    ) {
        val seekBar = view.findViewById<SeekBar>(seekId)
        val textView = view.findViewById<TextView>(textId)
        seekBar.progress = currentValue - 1
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                if (fromUser) textView.text = onChanged(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun updateConfig(update: (SensitivityConfig) -> SensitivityConfig) {
        val newConfig = update(SensitivityConfig.load(context))
        newConfig.save(context)
        SensitivityEngine.config = newConfig
    }
}
