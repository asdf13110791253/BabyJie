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

    private lateinit var windowManager: WindowManager
    private lateinit var floatRootView: View
    private lateinit var mainButton: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var tableClothView: TableClothView? = null
    private var isClothVisible = false

    private val params by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createFloatView()
    }

    private fun createFloatView() {
        floatRootView = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainButton = floatRootView.findViewById(R.id.floatMainBtn)
        menuBar = floatRootView.findViewById(R.id.menuBar)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 400
        windowManager.addView(floatRootView, params)

        mainButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()) > 10f) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(floatRootView, params)
                        if (isExpanded) hideMenu()
                        if (isClothVisible) hideTableCloth()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleMenu()
                    true
                }
                else -> false
            }
        }

        floatRootView.findViewById<TextView>(R.id.menuItem1).setOnClickListener {
            Toast.makeText(this, "AI分析", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        floatRootView.findViewById<TextView>(R.id.menuItem2).setOnClickListener {
            Toast.makeText(this, "台球参数", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        floatRootView.findViewById<TextView>(R.id.menuItem3).setOnClickListener {
            if (isClothVisible) hideTableCloth() else showTableCloth()
            hideMenu()
        }
    }

    private fun toggleMenu() {
        if (isExpanded) hideMenu() else showMenu()
    }

    private fun showMenu() { /* ... 与之前相同 ... */ }
    private fun hideMenu() { /* ... 与之前相同 ... */ }

    private fun showTableCloth() {
        if (isClothVisible) return
        isClothVisible = true
        tableClothView = TableClothView(this).apply {
            onDoneListener = { region -> hideTableCloth() }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(tableClothView, layoutParams)
    }

    private fun hideTableCloth() {
        if (!isClothVisible || tableClothView == null) return
        isClothVisible = false
        windowManager.removeView(tableClothView)
        tableClothView = null
    }

    override fun onDestroy() {
        if (isClothVisible && tableClothView != null) windowManager.removeView(tableClothView)
        if (::floatRootView.isInitialized) windowManager.removeView(floatRootView)
        super.onDestroy()
    }
}
