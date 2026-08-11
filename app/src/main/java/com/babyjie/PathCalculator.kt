package com.babyjie

import android.graphics.PointF
import kotlin.math.*

enum class LineType {
    STRAIGHT,      // 直球
    BANK,          // 翻袋
    MULTI_RAIL,    // 多酷
    CUSHION_SHOT,  // 护边球
    PASS           // 传球
}

data class AimLine(val start: PointF, val end: PointF, val type: LineType)
data class TargetInfo(val position: PointF, val ball: BallPosition, val score: Double)

class PathCalculator {
    private val pockets = listOf(
        Pocket(0.05f, 0.05f), Pocket(0.5f, 0.03f), Pocket(0.95f, 0.05f),
        Pocket(0.05f, 0.95f), Pocket(0.5f, 0.97f), Pocket(0.95f, 0.95f)
    )
    data class Pocket(val x: Float, val y: Float)

    // -------- 智能选球：找出角度最好、离袋口最近的目标球 --------
    fun findBestTarget(cue: PointF, balls: List<BallPosition>, w: Int, h: Int): TargetInfo? {
        var best: TargetInfo? = null
        var bestScore = Double.MAX_VALUE

        for (ball in balls) {
            if (ball.color == android.graphics.Color.WHITE || distance(cue.x, cue.y, ball.x, ball.y) < 25f) continue
            val targetPos = PointF(ball.x, ball.y)
            for (pocket in pockets) {
                val pocketPos = PointF(pocket.x * w, pocket.y * h)
                val toPocket = PointF(pocketPos.x - targetPos.x, pocketPos.y - targetPos.y)
                val cueToTarget = PointF(targetPos.x - cue.x, targetPos.y - cue.y)
                val angle = angleBetween(toPocket, cueToTarget)
                val distToPocket = distance(targetPos.x, targetPos.y, pocketPos.x, pocketPos.y)
                val score = angle * 0.7 + (distToPocket / (w + h)) * 0.3
                if (score < bestScore) {
                    bestScore = score
                    best = TargetInfo(targetPos, ball, score)
                }
            }
        }
        return best
    }

    // 直球（带摩擦力缩短）
    fun findStraightShot(cue: PointF, target: PointF, w: Int, h: Int): AimLine? {
        var best: AimLine? = null; var bestAngle = Double.MAX_VALUE
        for (p in pockets) {
            val px = p.x * w; val py = p.y * h
            val toPocket = PointF(px - target.x, py - target.y)
            val cueToTarget = PointF(target.x - cue.x, target.y - cue.y)
            val angle = angleBetween(toPocket, cueToTarget)
            if (angle < 0.15 && angle < bestAngle) {
                bestAngle = angle
                val dist = distance(target.x, target.y, px, py)
                val shorten = (dist * 0.02f).coerceAtMost(5f)
                val dirX = (px - target.x) / dist
                val dirY = (py - target.y) / dist
                val end = PointF(px - dirX * shorten, py - dirY * shorten)
                best = AimLine(PointF(target.x, target.y), end, LineType.STRAIGHT)
            }
        }
        return best
    }

    // 翻袋（目标球一库进袋）
    fun findBankShot(target: PointF, w: Int, h: Int): AimLine? {
        val mirrors = listOf(
            PointF(-target.x, target.y), PointF(2*w - target.x, target.y),
            PointF(target.x, -target.y), PointF(target.x, 2*h - target.y)
        )
        var best: AimLine? = null; var bestDist = Float.MAX_VALUE
        for (mirror in mirrors) {
            for (p in pockets) {
                val px = p.x * w; val py = p.y * h
                val d = distance(mirror.x, mirror.y, px, py)
                if (d < bestDist && d < w * 0.5f) {
                    bestDist = d
                    best = AimLine(PointF(target.x, target.y), mirror, LineType.BANK)
                }
            }
        }
        return best
    }

    // 多酷（白球两库）
    fun findMultiRailShot(cue: PointF, target: PointF, w: Int, h: Int): AimLine? {
        val rails = listOf(
            { p: PointF -> PointF(-p.x, p.y) },
            { p: PointF -> PointF(2*w - p.x, p.y) },
            { p: PointF -> PointF(p.x, -p.y) },
            { p: PointF -> PointF(p.x, 2*h - p.y) }
        )
        var best: AimLine? = null; var bestDist = Float.MAX_VALUE
        for (r1 in rails) {
            val m1 = r1(cue)
            for (r2 in rails) {
                val m2 = r2(m1)
                val d = distance(m2.x, m2.y, target.x, target.y)
                if (d < bestDist && d < w * 0.4f) {
                    bestDist = d
                    best = AimLine(cue, target, LineType.MULTI_RAIL)
                }
            }
        }
        return best
    }

    // 护边球：白球撞库一次后击中目标球
    fun findCushionShot(cue: PointF, target: PointF, w: Int, h: Int): AimLine? {
        val mirrors = listOf(
            PointF(-cue.x, cue.y), PointF(2*w - cue.x, cue.y),
            PointF(cue.x, -cue.y), PointF(cue.x, 2*h - cue.y)
        )
        var best: AimLine? = null; var bestAngle = Double.MAX_VALUE
        for (mirror in mirrors) {
            for (p in pockets) {
                val px = p.x * w; val py = p.y * h
                val toPocket = PointF(px - target.x, py - target.y)
                val mirrorToTarget = PointF(target.x - mirror.x, target.y - mirror.y)
                val angle = angleBetween(toPocket, mirrorToTarget)
                if (angle < 0.15 && angle < bestAngle) {
                    bestAngle = angle
                    best = AimLine(PointF(mirror.x, mirror.y), PointF(target.x, target.y), LineType.CUSHION_SHOT)
                }
            }
        }
        return best
    }

    // 传球
    fun findPassShot(cue: PointF, target: PointF, allBalls: List<BallPosition>, w: Int, h: Int): AimLine? {
        val middleBalls = allBalls.filter {
            distance(it.x, it.y, cue.x, cue.y) > 20 && distance(it.x, it.y, target.x, target.y) > 20
        }
        for (middle in middleBalls) {
            val midPos = PointF(middle.x, middle.y)
            val cueToMid = PointF(midPos.x - cue.x, midPos.y - cue.y)
            val midToTarget = PointF(target.x - midPos.x, target.y - midPos.y)
            if (angleBetween(cueToMid, midToTarget) < 0.2) {
                for (p in pockets) {
                    val px = p.x * w; val py = p.y * h
                    val targetToPocket = PointF(px - target.x, py - target.y)
                    if (angleBetween(midToTarget, targetToPocket) < 0.2) {
                        return AimLine(PointF(target.x, target.y), PointF(px, py), LineType.PASS)
                    }
                }
            }
        }
        return null
    }

    private fun angleBetween(v1: PointF, v2: PointF): Double {
        val dot = v1.x.toDouble() * v2.x.toDouble() + v1.y.toDouble() * v2.y.toDouble()
        val m1 = sqrt(v1.x.toDouble().pow(2) + v1.y.toDouble().pow(2))
        val m2 = sqrt(v2.x.toDouble().pow(2) + v2.y.toDouble().pow(2))
        return acos((dot / (m1 * m2)).coerceIn(-1.0, 1.0))
    }
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = (x1 - x2).toDouble(); val dy = (y1 - y2).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat()
    }
}
