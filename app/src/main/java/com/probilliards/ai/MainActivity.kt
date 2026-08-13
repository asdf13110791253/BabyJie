
// File: app/src/main/java/com/probilliards/ai/MainActivity.kt
package com.probilliards.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.probilliards.ai.databinding.ActivityMainBinding
import com.probilliards.ai.service.FloatingWindowService
import com.probilliards.ai.service.ScreenCaptureService
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity
 * 负责权限申请和服务启动
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    // 屏幕录制权限请求
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 通知权限请求（Android 13+）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkOverlayPermission()
        } else {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
            checkOverlayPermission()
        }
    }
    
    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_success, Toast.LENGTH_SHORT).show()
            requestScreenCapture()
        } else {
            Toast.makeText(this, R.string.overlay_permission_failed, Toast.LENGTH_SHORT).show()
        }
        updatePermissionStatus()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        
        setupClickListeners()
        updatePermissionStatus()
    }
    
    private fun setupClickListeners() {
        binding.btnStartAssistant.setOnClickListener {
            checkPermissionsAndStart()
        }
        
        binding.btnStopAssistant.setOnClickListener {
            stopAssistant()
        }
    }
    
    /**
     * 检查所有必要权限并按顺序请求
     */
    private fun checkPermissionsAndStart() {
        // 1. 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, 
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        // 2. 检查悬浮窗权限
        checkOverlayPermission()
    }
    
    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            Toast.makeText(this, R.string.overlay_permission_guide, Toast.LENGTH_LONG).show()
        } else {
            requestScreenCapture()
        }
    }
    
    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapture() {
        try {
            screenCaptureLauncher.launch(
                mediaProjectionManager.createScreenCaptureIntent()
            )
        } catch (e: Exception) {
            Toast.makeText(this, 
                "${getString(R.string.screen_capture_failed)}: ${e.message}", 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动屏幕采集服务
     */
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("resultData", data)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // 启动悬浮窗服务
        startFloatingWindowService()
        
        Toast.makeText(this, R.string.assistant_started, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    /**
     * 停止辅助
     */
    private fun stopAssistant() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, FloatingWindowService::class.java))
        Toast.makeText(this, R.string.assistant_stopped, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 更新权限状态显示
     */
    private fun updatePermissionStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (overlayGranted) {
            getString(R.string.overlay_permission_granted)
        } else {
            getString(R.string.overlay_permission_status)
        }
        binding.tvOverlayStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (overlayGranted) android.R.color.holo_green_dark 
                else android.R.color.holo_red_dark
            )
        )
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}
