// File: app/src/main/java/com/probilliards/ai/ai/ShotRecommender.kt
package com.probilliards.ai.ai

import android.graphics.PointF
import com.probilliards.ai.util.GeometryUtils
import com.probilliards.ai.vision.Ball
import kotlin.math.*

data class ShotRecommendation(
    val whiteBall: Ball,
    val targetBall: Ball,
    val pocketPosition: PointF,
    val probability: Float,
    val power: Float,
    val path: List<PointF>,
    val score: Float,
    val shotType: ShotType = ShotType.OFFENSIVE,
    val cueAction: CueAction = CueAction.CENTER,
    val whiteBallFinalPosition: PointF = PointF(0f, 0f),
    val isDefensive: Boolean = false
)

enum class ShotType {
    OFFENSIVE, DEFENSIVE, SAFETY
}

enum class CueAction {
    CENTER, TOP, BOTTOM, LEFT, RIGHT,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

enum class AIMode {
    OFFENSIVE, DEFENSIVE, BALANCED
}

class ShotRecommender {
    
    companion object {
        private const val TAG = "ShotRecommender"
        private const val BALL_RADIUS = 15f
    }
    
    fun recommendShot(
        whiteBall: Ball,
        balls: List<Ball>,
        pockets: List<PointF>,
        mode: AIMode = AIMode.BALANCED
    ): ShotRecommendation? {
        var bestShot: ShotRecommendation? = null
        var bestScore = 0f
        
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
        
        return bestShot
    }
    
    private fun evaluateShot(
        whiteBall: Ball,
        targetBall: Ball,
        pocket: PointF
    ): ShotRecommendation? {
        val whitePos = whiteBall.position
        val targetPos = targetBall.position
        
        val targetToPocketDx = pocket.x - targetPos.x
        val targetToPocketDy = pocket.y - targetPos.y
        val targetToPocketDist = sqrt(targetToPocketDx * targetToPocketDx + targetToPocketDy * targetToPocketDy)
        
        if (targetToPocketDist < 1) return null
        
        val unitX = targetToPocketDx / targetToPocketDist
        val unitY = targetToPocketDy / targetToPocketDist
        
        val ballDiameter = targetBall.radius * 2
        val aimX = targetPos.x - unitX * ballDiameter
        val aimY = targetPos.y - unitY * ballDiameter
        
        val whiteToAimDx = aimX - whitePos.x
        val whiteToAimDy = aimY - whitePos.y
        val whiteToAimDist = sqrt(whiteToAimDx * whiteToAimDx + whiteToAimDy * whiteToAimDy)
        
        val angleDeviation = calculateAngleDeviation(whitePos, aimX, aimY, targetPos, pocket)
        val probability = calculateProbability(angleDeviation, whiteToAimDist, targetToPocketDist)
        val power = calculatePower(whiteToAimDist, targetToPocketDist)
        val score = probability * 100 - whiteToAimDist * 0.01f - angleDeviation * 10
        
        val path = listOf(whitePos, PointF(aimX, aimY), pocket)
        
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
    
    private fun calculateAngleDeviation(
        whitePos: PointF, aimX: Float, aimY: Float,
        targetPos: PointF, pocket: PointF
    ): Float {
        val whiteToAimAngle = atan2(aimY - whitePos.y, aimX - whitePos.x)
        val targetToPocketAngle = atan2(pocket.y - targetPos.y, pocket.x - targetPos.x)
        var angleDiff = abs(whiteToAimAngle - targetToPocketAngle)
        if (angleDiff > PI) angleDiff = (2 * PI - angleDiff).toFloat()
        return angleDiff
    }
    
    private fun calculateProbability(
        angleDeviation: Float, whiteToAimDist: Float, targetToPocketDist: Float
    ): Float {
        val angleFactor = max(0f, 1f - angleDeviation / (PI.toFloat() / 4))
        val distanceFactor = max(0f, 1f - (whiteToAimDist + targetToPocketDist) / 2000f)
        return angleFactor * 0.7f + distanceFactor * 0.3f
    }
    
    private fun calculatePower(whiteToAimDist: Float, targetToPocketDist: Float): Float {
        val totalDist = whiteToAimDist + targetToPocketDist
        return min(1f, max(0.2f, totalDist / 500f))
    }
}
