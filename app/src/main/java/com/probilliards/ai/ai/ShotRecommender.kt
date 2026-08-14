package com.probilliards.ai.ai

import android.graphics.PointF
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

enum class ShotType { OFFENSIVE, DEFENSIVE, SAFETY }
enum class CueAction { CENTER, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class AIMode { OFFENSIVE, DEFENSIVE, BALANCED }

class ShotRecommender {
    
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
    
    private fun evaluateShot(whiteBall: Ball, targetBall: Ball, pocket: PointF): ShotRecommendation? {
        val whitePos = whiteBall.position
        val targetPos = targetBall.position
        val dx = pocket.x - targetPos.x
        val dy = pocket.y - targetPos.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1) return null
        val unitX = dx / dist
        val unitY = dy / dist
        val ballDiameter = targetBall.radius * 2
        val aimX = targetPos.x - unitX * ballDiameter
        val aimY = targetPos.y - unitY * ballDiameter
        val whiteDx = aimX - whitePos.x
        val whiteDy = aimY - whitePos.y
        val whiteDist = sqrt(whiteDx * whiteDx + whiteDy * whiteDy)
        val probability = max(0f, 1f - whiteDist / 2000f)
        val power = min(1f, max(0.2f, (whiteDist + dist) / 500f))
        val score = probability * 100
        return ShotRecommendation(
            whiteBall = whiteBall,
            targetBall = targetBall,
            pocketPosition = pocket,
            probability = probability,
            power = power,
            path = listOf(whitePos, PointF(aimX, aimY), pocket),
            score = score
        )
    }
}
