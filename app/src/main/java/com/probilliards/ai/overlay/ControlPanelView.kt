// File: app/src/main/java/com/probilliards/ai/overlay/ControlPanelView.kt
package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Switch
import androidx.cardview.widget.CardView
import com.probilliards.ai.R

/**
 * 控制面板视图（更新版）
 * 包含所有功能开关和按钮，支持动画
 */
class ControlPanelView(context: Context) : CardView(context) {
    
    var onToggleChange: ((String, Boolean) -> Unit)? = null
    var onAdjustAreaClick: (() -> Unit)? = null
    var onOpenSettingsClick: (() -> Unit)? = null
    var onCloseClick: (() -> Unit)? = null
    
    private val switches = mutableMapOf<String, Switch>()
    
    init {
        initView()
    }
    
    private fun initView() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.control_panel, this, true)
        
        radius = 16f
        cardElevation = 8f
        setContentPadding(16, 16, 16, 16)
        
        // 设置背景
        setCardBackgroundColor(Color.argb(230, 30, 30, 30))
        
        // 初始化开关
        setupSwitch(view, R.id.switchMainLine, "main_line", context.getString(R.string.main_line))
        setupSwitch(view, R.id.switchTargetLine, "target_line", context.getString(R.string.target_line))
        setupSwitch(view, R.id.switchReboundLine, "rebound_line", context.getString(R.string.rebound_line))
        setupSwitch(view, R.id.switchPowerBar, "power_bar", context.getString(R.string.power_bar))
        setupSwitch(view, R.id.switchAIRecommend, "ai_recommend", context.getString(R.string.ai_recommend))
        
        // 设置按钮点击事件
        view.findViewById<Button>(R.id.btnAdjustArea).setOnClickListener {
            onAdjustAreaClick?.invoke()
        }
        
        view.findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            onOpenSettingsClick?.invoke()
        }
        
        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            onCloseClick?.invoke()
        }
    }
    
    private fun setupSwitch(root: View, switchId: Int, key: String, label: String) {
        val switchView = root.findViewById<Switch>(switchId)
        switchView.text = label
        switchView.setTextColor(Color.WHITE)
        
        // 从SharedPreferences加载状态
        val prefs = context.getSharedPreferences("probilliards_prefs", Context.MODE_PRIVATE)
        switchView.isChecked = prefs.getBoolean("feature_$key", 
            when(key) {
                "main_line" -> true
                "ai_recommend" -> true
                else -> false
            }
        )
        
        switchView.setOnCheckedChangeListener { _, isChecked ->
            onToggleChange?.invoke(key, isChecked)
            prefs.edit().putBoolean("feature_$key", isChecked).apply()
        }
        
        switches[key] = switchView
    }
    
    fun getSwitchState(key: String): Boolean {
        return switches[key]?.isChecked ?: false
    }
    
    /**
     * 设置所有开关状态
     */
    fun setAllSwitches(enabled: Boolean) {
        switches.values.forEach { it.isChecked = enabled }
    }
    
    /**
     * 获取所有开关状态
     */
    fun getAllSwitchStates(): Map<String, Boolean> {
        return switches.mapValues { it.value.isChecked }
    }
}
