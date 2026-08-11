package com.babyjie

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatWindowService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var menuExpanded = false
    private var initX = 0
    private var initY = 0
    private var initTX = 0f
    private var initTY = 0f
    private var dragging = false

    private var modeBar: View? = null
    private var modeVisible = false

    private var displayMetrics = DisplayMetrics()

    private val floatParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm.defaultDisplay.getMetrics(displayMetrics)
        createFloatView()
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)
        menuBar = floatRoot.findViewById(R.id.menuBar)

        floatParams.gravity = Gravity.TOP or Gravity.START
        floatParams.x = 100
        floatParams.y = 400
        wm.addView(floatRoot, floatParams)

        // 主按钮拖动
        mainBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = floatParams.x
                    initY = floatParams.y
                    initTX = ev.rawX
                    initTY = ev.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initTX
                    val dy = ev.rawY - initTY
                    if (Math.sqrt((dx * dx + dy * dy).toDouble()) > 10f) dragging = true
                    if (dragging) {
                        floatParams.x = initX + dx.toInt()
                        floatParams.y = initY + dy.toInt()
                        wm.updateViewLayout(floatRoot, floatParams)
                        if (menuExpanded) hideMenu()
                        if (modeVisible) hideModeBar()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) toggleMenu()
                    true
                }
                else -> false
            }
        }

        // 菜单项1：AI 模式选择
        floatRoot.findViewById<TextView>(R.id.menuItem1).setOnClickListener {
            if (modeVisible) hideModeBar() else showModeBar()
            hideMenu()
        }
        // 菜单项2：台球参数（暂未开放）
        floatRoot.findViewById<TextView>(R.id.menuItem2).setOnClickListener {
            Toast.makeText(this, "台球参数尚未开放", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        // 菜单项3：桌布调整（暂未开放）
        floatRoot.findViewById<TextView>(R.id.menuItem3).setOnClickListener {
            Toast.makeText(this, "桌布调整尚未开放", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
    }

    private fun toggleMenu() {
        if (menuExpanded) hideMenu() else showMenu()
    }

    private fun showMenu() {
        if (menuExpanded) return
        menuExpanded = true
        val sw = displayMetrics.widthPixels
        val menuW = (sw * 0.55f).toInt()
        menuBar.layoutParams.width = menuW
        val loc = IntArray(2)
        mainBtn.getLocationOnScreen(loc)
        val cx = loc[0] + mainBtn.width / 2f
        val top = loc[1]
        val mx = (cx - menuW / 2f).toInt().coerceIn(0, sw - menuW)
        val my = (top - menuBar.layoutParams.height - 20).coerceAtLeast(0)
        menuBar.x = mx.toFloat()
        menuBar.y = my.toFloat()
        menuBar.visibility = View.VISIBLE
        menuBar.alpha = 0f
        menuBar.scaleX = 0.8f
        menuBar.scaleY = 0.8f
        menuBar.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun hideMenu() {
        if (!menuExpanded) return
        menuExpanded = false
        menuBar.animate()
            .alpha(0f).scaleX(0.8f).scaleY(0.8f)
            .setDuration(150)
            .withEndAction { menuBar.visibility = View.GONE }
            .start()
    }

    private fun showModeBar() {
        if (modeVisible) return
        modeVisible = true
        modeBar = LayoutInflater.from(this).inflate(R.layout.mode_selector, null)
        modeBar?.findViewById<TextView>(R.id.btnStraight)?.setOnClickListener {
            Toast.makeText(this, "直球模式", Toast.LENGTH_SHORT).show()
            hideModeBar()
        }
        modeBar?.findViewById<TextView>(R.id.btnBank)?.setOnClickListener {
            Toast.makeText(this, "翻袋模式", Toast.LENGTH_SHORT).show()
            hideModeBar()
        }
        modeBar?.findViewById<TextView>(R.id.btnMultiRail)?.setOnClickListener {
            Toast.makeText(this, "多酷模式", Toast.LENGTH_SHORT).show()
            hideModeBar()
        }
        modeBar?.findViewById<TextView>(R.id.btnRailCut)?.setOnClickListener {
            Toast.makeText(this, "护边球模式", Toast.LENGTH_SHORT).show()
            hideModeBar()
        }
        modeBar?.findViewById<TextView>(R.id.btnPass)?.setOnClickListener {
            Toast.makeText(this, "传球模式", Toast.LENGTH_SHORT).show()
            hideModeBar()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 200
        }
        wm.addView(modeBar, lp)
    }

    private fun hideModeBar() {
        if (!modeVisible || modeBar == null) return
        modeVisible = false
        wm.removeView(modeBar)
        modeBar = null
    }

    override fun onDestroy() {
        if (modeVisible && modeBar != null) wm.removeView(modeBar)
        if (::floatRoot.isInitialized) wm.removeView(floatRoot)
        super.onDestroy()
    }
}
