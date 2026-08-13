// File: app/src/main/java/com/probilliards/ai/util/GeometryUtils.kt
package com.probilliards.ai.util

import android.graphics.PointF
import android.graphics.RectF
import org.opencv.core.Point
import kotlin.math.*

/**
 * 几何计算工具类
 */
object GeometryUtils {
    
    /**
     * 计算两点之间的距离（PointF）
     */
    fun distance(p1: PointF, p2: PointF): Float {
        return distance(p1.x, p1.y, p2.x, p2.y)
    }
    
    /**
     * 计算两点之间的距离（Float）
     */
    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 计算两点之间的距离（Double）
     */
    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 计算两点之间的角度（弧度）
     */
    fun angle(p1: PointF, p2: PointF): Float {
        return atan2(p2.y - p1.y, p2.x - p1.x)
    }
    
    /**
     * 计算两点之间的角度（度）
     */
    fun angleDegrees(p1: PointF, p2: PointF): Float {
        return Math.toDegrees(angle(p1, p2).toDouble()).toFloat()
    }
    
    /**
     * 计算三个点形成的角度
     */
    fun angleBetween(p1: PointF, p2: PointF, p3: PointF): Float {
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        val cosAngle = (dotProduct / (mag1 * mag2)).coerceIn(-1f, 1f)
        return acos(cosAngle)
    }
    
    /**
     * 归一化向量
     */
    fun normalize(x: Float, y: Float): PointF {
        val magnitude = sqrt(x * x + y * y)
        if (magnitude == 0f) return PointF(0f, 0f)
        return PointF(x / magnitude, y / magnitude)
    }
    
    /**
     * 归一化向量（PointF）
     */
    fun normalize(p: PointF): PointF {
        return normalize(p.x, p.y)
    }
    
    /**
     * 计算向量长度
     */
    fun magnitude(x: Float, y: Float): Float {
        return sqrt(x * x + y * y)
    }
    
    /**
     * 计算向量长度（PointF）
     */
    fun magnitude(p: PointF): Float {
        return magnitude(p.x, p.y)
    }
    
    /**
     * 计算两点之间的中点
     */
    fun midpoint(p1: PointF, p2: PointF): PointF {
        return PointF((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
    }
    
    /**
     * 计算线段上的点
     */
    fun pointOnLine(p1: PointF, p2: PointF, t: Float): PointF {
        return PointF(
            p1.x + (p2.x - p1.x) * t,
            p1.y + (p2.y - p1.y) * t
        )
    }
    
    /**
     * 计算点到直线的距离
     */
    fun distanceToLine(point: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val a = lineEnd.y - lineStart.y
        val b = lineStart.x - lineEnd.x
        val c = lineEnd.x * lineStart.y - lineStart.x * lineEnd.y
        
        return abs(a * point.x + b * point.y + c) / sqrt(a * a + b * b)
    }
    
    /**
     * 计算点到线段的最短距离
     */
    fun distanceToSegment(point: PointF, segmentStart: PointF, segmentEnd: PointF): Float {
        val dx = segmentEnd.x - segmentStart.x
        val dy = segmentEnd.y - segmentStart.y
        
        if (dx == 0f && dy == 0f) {
            return distance(point, segmentStart)
        }
        
        val t = ((point.x - segmentStart.x) * dx + (point.y - segmentStart.y) * dy) / 
                (dx * dx + dy * dy)
        
        val clampedT = t.coerceIn(0f, 1f)
        val projectionX = segmentStart.x + clampedT * dx
        val projectionY = segmentStart.y + clampedT * dy
        
        return distance(point.x, point.y, projectionX, projectionY)
    }
    
    /**
     * 计算两条线段的交点
     */
    fun lineIntersection(
        p1: PointF, p2: PointF,
        p3: PointF, p4: PointF
    ): PointF? {
        val d = (p2.x - p1.x) * (p4.y - p3.y) - (p2.y - p1.y) * (p4.x - p3.x)
        
        if (abs(d) < 0.0001f) return null
        
        val t = ((p3.x - p1.x) * (p4.y - p3.y) - (p3.y - p1.y) * (p4.x - p3.x)) / d
        val u = ((p3.x - p1.x) * (p2.y - p1.y) - (p3.y - p1.y) * (p2.x - p1.x)) / d
        
        if (t in 0f..1f && u in 0f..1f) {
            return PointF(
                p1.x + t * (p2.x - p1.x),
                p1.y + t * (p2.y - p1.y)
            )
        }
        
        return null
    }
    
    /**
     * 计算射线与线段的交点
     */
    fun rayIntersection(
        rayOrigin: PointF,
        rayDirection: PointF,
        segmentStart: PointF,
        segmentEnd: PointF
    ): PointF? {
        val p1 = rayOrigin
        val p2 = PointF(
            rayOrigin.x + rayDirection.x * 10000f, 
            rayOrigin.y + rayDirection.y * 10000f
        )
        
        return lineIntersection(p1, p2, segmentStart, segmentEnd)
    }
    
    /**
     * 计算反射向量
     */
    fun reflect(incident: PointF, normal: PointF): PointF {
        val dot = incident.x * normal.x + incident.y * normal.y
        return PointF(
            incident.x - 2 * dot * normal.x,
            incident.y - 2 * dot * normal.y
        )
    }
    
    /**
     * 计算台球碰撞
     */
    fun calculateCollision(
        whiteBall: PointF,
        targetBall: PointF,
        pocket: PointF,
        ballRadius: Float = 15f
    ): CollisionResult {
        val targetToPocket = normalize(
            pocket.x - targetBall.x,
            pocket.y - targetBall.y
        )
        
        val aimPoint = PointF(
            targetBall.x - targetToPocket.x * ballRadius * 2,
            targetBall.y - targetToPocket.y * ballRadius * 2
        )
        
        val whiteToAim = normalize(
            aimPoint.x - whiteBall.x,
            aimPoint.y - whiteBall.y
        )
        
        val collisionNormal = normalize(
            aimPoint.x - targetBall.x,
            aimPoint.y - targetBall.y
        )
        val whiteDirection = reflect(whiteToAim, collisionNormal)
        
        return CollisionResult(
            aimPoint = aimPoint,
            whiteBallDirection = whiteDirection,
            targetBallDirection = targetToPocket
        )
    }
    
    /**
     * 检查点是否在矩形内
     */
    fun isPointInRect(point: PointF, rect: RectF): Boolean {
        return point.x >= rect.left && point.x <= rect.right &&
               point.y >= rect.top && point.y <= rect.bottom
    }
    
    /**
     * 检查点是否在圆内
     */
    fun isPointInCircle(point: PointF, center: PointF, radius: Float): Boolean {
        return distance(point, center) <= radius
    }
    
    /**
     * 检查两个圆是否相交
     */
    fun circlesIntersect(
        center1: PointF, radius1: Float,
        center2: PointF, radius2: Float
    ): Boolean {
        return distance(center1, center2) <= (radius1 + radius2)
    }
    
    /**
     * 将角度转换为方向向量
     */
    fun angleToVector(angleRadians: Float): PointF {
        return PointF(cos(angleRadians), sin(angleRadians))
    }
    
    /**
     * 将方向向量转换为角度
     */
    fun vectorToAngle(direction: PointF): Float {
        return atan2(direction.y, direction.x)
    }
    
    /**
     * 限制值在指定范围内
     */
    fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }
    
    /**
     * 线性插值
     */
    fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * clamp(t, 0f, 1f)
    }
    
    /**
     * 计算两点之间的线性插值点
     */
    fun lerpPoint(p1: PointF, p2: PointF, t: Float): PointF {
        return PointF(
            lerp(p1.x, p2.x, t),
            lerp(p1.y, p2.y, t)
        )
    }
    
    data class ReflectionResult(
        val reflectionPoint: PointF,
        val reflectedDirection: PointF,
        val distance: Float
    )
    
    data class CollisionResult(
        val aimPoint: PointF,
        val whiteBallDirection: PointF,
        val targetBallDirection: PointF
    )
}
