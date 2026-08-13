
// File: app/src/main/java/com/probilliards/ai/viewmodel/MainViewModel.kt
package com.probilliards.ai.viewmodel

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.probilliards.ai.ai.ShotRecommendation
import com.probilliards.ai.ai.ShotRecommender
import com.probilliards.ai.vision.Ball
import com.probilliards.ai.vision.BallDetector
import com.probilliards.ai.vision.PocketDetector
import com.probilliards.ai.vision.TableDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 主ViewModel
 * 负责图像处理和AI推荐
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tableDetector: TableDetector,
    private val ballDetector: BallDetector,
    private val pocketDetector: PocketDetector,
    private val shotRecommender: ShotRecommender
) : ViewModel() {
    
    private val _balls = MutableLiveData<List<Ball>>()
    val balls: LiveData<List<Ball>> = _balls
    
    private val _recommendation = MutableLiveData<ShotRecommendation>()
    val recommendation: LiveData<ShotRecommendation> = _recommendation
    
    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing
    
    /**
     * 处理屏幕帧
     */
    fun processFrame(bitmap: Bitmap, selectionRect: android.graphics.Rect? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.postValue(true)
            
            try {
                // 检测球桌
                val tableCorners = tableDetector.detectTable(
                    bitmap, 
                    selectionRect?.let { 
                        org.opencv.core.Rect(it.left, it.top, it.width(), it.height()) 
                    }
                )
                
                // 检测球
                val detectedBalls = ballDetector.detectBalls(bitmap)
                _balls.postValue(detectedBalls)
                
                // 找到白球
                val whiteBall = detectedBalls.find { it.isWhite }
                
                if (whiteBall != null) {
                    // 获取袋口位置
                    val pockets = if (tableCorners != null) {
                        pocketDetector.detectPockets(tableCorners)
                            .map { it.position }
                    } else {
                        pocketDetector.estimatePockets(
                            bitmap.width, 
                            bitmap.height,
                            selectionRect
                        ).map { it.position }
                    }
                    
                    // 计算AI推荐
                    val recommendation = shotRecommender.recommendShot(
                        whiteBall,
                        detectedBalls.filter { !it.isWhite },
                        pockets
                    )
                    
                    _recommendation.postValue(recommendation)
                }
            } catch (e: Exception) {
                // 处理异常
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }
}
