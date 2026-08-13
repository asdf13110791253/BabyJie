// File: app/src/main/java/com/probilliards/ai/ai/ShotRecommender.kt
package com.probilliards.ai.ai

import android.graphics.PointF
import android.util.Log
import com.probilliards.ai.vision.Ball
import kotlin.math.*

/**
 * 击球推荐数据类
 */
data class ShotRecommendation(
    val whiteBall: Ball,
    val targetBall: Ball,
    val pocketPosition: PointF,
    val probability: Float,
    val power: Float,
    val path: List<PointF>,
    val score: Float
)

/**
 * AI击球推荐器
 * 遍历所有目标球和袋口组合，选出最优路线
 */
class ShotRecommender {
    
    companion object {
        private const val TAG = "ShotRecommender"
    }
    
    /**
     * 计算最优击球路线
     */
    fun recommendShot(
        whiteBall: Ball,
        balls: List<Ball>,
        pockets: List<PointF>
    ): ShotRecommendation? {
        var bestShot: ShotRecommendation? = null
        var bestScore = 0f
        
        // 遍历所有目标球和袋口组合
        for (targetBall in balls) {
            if (targetBall.isWhite) continue
            
            for (pocket in pockets) {
                val shot = evaluateShot(whiteBall, targetBall, pocket)
                if (shot != null && shot.score > bestScore) {
                    bestScore = shot.score
                    bestShot = shot
                }
            }
        }
        
        if (bestShot != null) {
            Log.d(TAG, "推荐击球: 概率=${bestShot.probability}, 力度=${bestShot.power}, 得分=${bestShot.score}")
        }
        
        return bestShot
    }
    
    /**
     * 评估单个击球方案
     */
    private fun evaluateShot(
        whiteBall: Ball,
        targetBall: Ball,
        pocket: PointF
    ): ShotRecommendation? {
        val whitePos = whiteBall.position
        val targetPos = targetBall.position
        
        // 计算目标球到袋口的方向
        val targetToPocketDx = pocket.x - targetPos.x
        val targetToPocketDy = pocket.y - targetPos.y
        val targetToPocketDist = sqrt(
            targetToPocketDx * targetToPocketDx + 
            targetToPocketDy * targetToPocketDy
        )
        
        if (targetToPocketDist < 1) return null
        
        val targetToPocketUnitX = targetToPocketDx / targetToPocketDist
        val targetToPocketUnitY = targetToPocketDy / targetToPocketDist
        
        // 计算白球需要击打的位置（目标球后方）
        val ballDiameter = targetBall.radius * 2
        val aimX = targetPos.x - targetToPocketUnitX * ballDiameter
        val aimY = targetPos.y - targetToPocketUnitY * ballDiameter
        
        // 计算白球到瞄准点的距离
        val whiteToAimDx = aimX - whitePos.x
        val whiteToAimDy = aimY - whitePos.y
        val whiteToAimDist = sqrt(
            whiteToAimDx * whiteToAimDx + 
            whiteToAimDy * whiteToAimDy
        )
        
        // 计算角度偏差
        val angleDeviation = calculateAngleDeviation(
            whitePos, aimX, aimY, targetPos, pocket
        )
        
        // 检查是否有障碍球（简化处理）
        val hasObstacle = false // 实际项目中需要实现碰撞检测
        
        if (hasObstacle) {
            return null
        }
        
        // 计算进球概率
        val probability = calculateProbability(angleDeviation, whiteToAimDist, targetToPocketDist)
        
        // 计算建议力度
        val power = calculatePower(whiteToAimDist, targetToPocketDist)
        
        // 计算综合得分
        val score = probability * 100 - whiteToAimDist * 0.01f - angleDeviation * 10
        
        // 生成路径
        val path = generatePath(whitePos, PointF(aimX, aimY), pocket)
        
        return ShotRecommendation(
            whiteBall = whiteBall,
            targetBall = targetBall,
            pocketPosition = pocket,
            probability = probability,
            power = power,
            path = path,
            score = score
        )
    }
    
    /**
     * 计算角度偏差
     */
    private fun calculateAngleDeviation(
        whitePos: PointF,
        aimX: Float,
        aimY: Float,
        targetPos: PointF,
        pocket: PointF
    ): Float {
        val whiteToAimAngle = atan2(aimY - whitePos.y, aimX - whitePos.x)
        val targetToPocketAngle = atan2(
            pocket.y - targetPos.y, 
            pocket.x - targetPos.x
        )
        
        var angleDiff = abs(whiteToAimAngle - targetToPocketAngle)
        if (angleDiff > PI) {
            angleDiff = (2 * PI - angleDiff).toFloat()
        }
        
        return angleDiff
    }
    
    /**
     * 计算进球概率
     */
    private fun calculateProbability(
        angleDeviation: Float,
        whiteToAimDist: Float,
        targetToPocketDist: Float
    ): Float {
        // 角度偏差越小，概率越高
        val angleFactor = max(0f, 1f - angleDeviation / (PI / 4))
        
        // 距离越近，概率越高
        val distanceFactor = max(0f, 1f - (whiteToAimDist + targetToPocketDist) / 2000f)
        
        return angleFactor * 0.7f + distanceFactor * 0.3f
    }
    
    /**
     * 计算建议力度
     */
    private fun calculatePower(whiteToAimDist: Float, targetToPocketDist: Float): Float {
        val totalDist = whiteToAimDist + targetToPocketDist
        return min(1f, max(0.2f, totalDist / 500f))
    }
    
    /**
     * 生成击球路径
     */
    private fun generatePath(
        whitePos: PointF,
        aimPoint: PointF,
        pocket: PointF
    ): List<PointF> {
        return listOf(whitePos, aimPoint, pocket)
    }
}
