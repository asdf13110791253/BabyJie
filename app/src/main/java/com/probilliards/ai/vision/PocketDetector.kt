package com.probilliards.ai.vision

import android.graphics.PointF

class PocketDetector {
    
    data class PocketInfo(
        val position: PointF,
        val type: PocketType,
        val radius: Float
    )
    
    enum class PocketType {
        TOP_LEFT, TOP_MIDDLE, TOP_RIGHT,
        BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT
    }
    
    fun estimatePockets(
        screenWidth: Int,
        screenHeight: Int,
        selectionRect: android.graphics.Rect? = null
    ): List<PocketInfo> {
        val left = selectionRect?.left?.toFloat() ?: 0f
        val top = selectionRect?.top?.toFloat() ?: 0f
        val right = selectionRect?.right?.toFloat() ?: screenWidth.toFloat()
        val bottom = selectionRect?.bottom?.toFloat() ?: screenHeight.toFloat()
        val width = right - left
        val height = bottom - top
        val pocketRadius = minOf(width, height) * 0.04f
        return listOf(
            PocketInfo(PointF(left + width * 0.08f, top + height * 0.08f), PocketType.TOP_LEFT, pocketRadius),
            PocketInfo(PointF(left + width / 2, top + height * 0.06f), PocketType.TOP_MIDDLE, pocketRadius * 1.2f),
            PocketInfo(PointF(right - width * 0.08f, top + height * 0.08f), PocketType.TOP_RIGHT, pocketRadius),
            PocketInfo(PointF(left + width * 0.08f, bottom - height * 0.08f), PocketType.BOTTOM_LEFT, pocketRadius),
            PocketInfo(PointF(left + width / 2, bottom - height * 0.06f), PocketType.BOTTOM_MIDDLE, pocketRadius * 1.2f),
            PocketInfo(PointF(right - width * 0.08f, bottom - height * 0.08f), PocketType.BOTTOM_RIGHT, pocketRadius)
        )
    }
}
