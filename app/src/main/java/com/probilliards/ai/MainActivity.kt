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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "用户拒绝了屏幕录制权限", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        checkOverlayPermission()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
                as MediaProjectionManager
            
            setupClickListeners()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnStartAssistant.setOnClickListener {
            checkPermissionsAndStart()
        }
        
        binding.btnStopAssistant.setOnClickListener {
            stopAssistant()
        }
    }
    
    private fun checkPermissionsAndStart() {
        try {
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
            checkOverlayPermission()
        } catch (e: Exception) {
            Toast.makeText(this, "权限检查失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            requestScreenCapture()
        }
    }
    
    private fun requestScreenCapture() {
        try {
            screenCaptureLauncher.launch(
                mediaProjectionManager.createScreenCaptureIntent()
            )
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动屏幕录制: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        try {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            startFloatingWindowService()
            Toast.makeText(this, "ProBilliards AI 已启动", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startFloatingWindowService() {
        try {
            val intent = Intent(this, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopAssistant() {
        try {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, FloatingWindowService::class.java))
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "停止失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
