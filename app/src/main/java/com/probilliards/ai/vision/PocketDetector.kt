
// File: app/src/main/java/com/probilliards/ai/vision/PocketDetector.kt
package com.probilliards.ai.vision

import android.graphics.PointF
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import kotlin.math.hypot
import kotlin.math.atan2

/**
 * 袋口检测器
 * 根据球桌四边形顶点计算6个袋口位置
 */
class PocketDetector {
    
    companion object {
        private const val TAG = "PocketDetector"
        
        // 袋口偏移比例
        private const val CORNER_POCKET_OFFSET_X = 0.08f
        private const val CORNER_POCKET_OFFSET_Y = 0.08f
        private const val MIDDLE_POCKET_OFFSET = 0.06f
    }
    
    data class PocketInfo(
        val position: PointF,
        val type: PocketType,
        val radius: Float
    )
    
    enum class PocketType {
        TOP_LEFT, TOP_MIDDLE, TOP_RIGHT,
        BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT
    }
    
    /**
     * 检测球桌上的6个袋口位置
     */
    fun detectPockets(tableCorners: MatOfPoint2f): List<PocketInfo> {
        val corners = tableCorners.toArray()
        if (corners.size != 4) {
            return emptyList()
        }
        
        val orderedCorners = orderCorners(corners)
        
        val topLeft = orderedCorners[0]
        val topRight = orderedCorners[1]
        val bottomRight = orderedCorners[2]
        val bottomLeft = orderedCorners[3]
        
        val tableWidth = hypot(
            (topRight.x - topLeft.x),
            (topRight.y - topLeft.y)
        ).toFloat()
        val tableHeight = hypot(
            (bottomLeft.x - topLeft.x),
            (bottomLeft.y - topLeft.y)
        ).toFloat()
        
        val pocketRadius = minOf(tableWidth, tableHeight) * 0.04f
        
        val pockets = mutableListOf<PocketInfo>()
        
        // 左上角袋
        pockets.add(PocketInfo(
            position = PointF(
                (topLeft.x + (topRight.x - topLeft.x) * CORNER_POCKET_OFFSET_X).toFloat(),
                (topLeft.y + (bottomLeft.y - topLeft.y) * CORNER_POCKET_OFFSET_Y).toFloat()
            ),
            type = PocketType.TOP_LEFT,
            radius = pocketRadius
        ))
        
        // 上中袋
        pockets.add(PocketInfo(
            position = PointF(
                ((topLeft.x + topRight.x) / 2).toFloat(),
                (topLeft.y + (bottomLeft.y - topLeft.y) * MIDDLE_POCKET_OFFSET).toFloat()
            ),
            type = PocketType.TOP_MIDDLE,
            radius = pocketRadius * 1.2f
        ))
        
        // 右上角袋
        pockets.add(PocketInfo(
            position = PointF(
                (topRight.x - (topRight.x - topLeft.x) * CORNER_POCKET_OFFSET_X).toFloat(),
                (topRight.y + (bottomRight.y - topRight.y) * CORNER_POCKET_OFFSET_Y).toFloat()
            ),
            type = PocketType.TOP_RIGHT,
            radius = pocketRadius
        ))
        
        // 左下角袋
        pockets.add(PocketInfo(
            position = PointF(
                (bottomLeft.x + (bottomRight.x - bottomLeft.x) * CORNER_POCKET_OFFSET_X).toFloat(),
                (bottomLeft.y - (bottomLeft.y - topLeft.y) * CORNER_POCKET_OFFSET_Y).toFloat()
            ),
            type = PocketType.BOTTOM_LEFT,
            radius = pocketRadius
        ))
        
        // 下中袋
        pockets.add(PocketInfo(
            position = PointF(
                ((bottomLeft.x + bottomRight.x) / 2).toFloat(),
                (bottomLeft.y - (bottomLeft.y - topLeft.y) * MIDDLE_POCKET_OFFSET).toFloat()
            ),
            type = PocketType.BOTTOM_MIDDLE,
            radius = pocketRadius * 1.2f
        ))
        
        // 右下角袋
        pockets.add(PocketInfo(
            position = PointF(
                (bottomRight.x - (bottomRight.x - bottomLeft.x) * CORNER_POCKET_OFFSET_X).toFloat(),
                (bottomRight.y - (bottomRight.y - topRight.y) * CORNER_POCKET_OFFSET_Y).toFloat()
            ),
            type = PocketType.BOTTOM_RIGHT,
            radius = pocketRadius
        ))
        
        return pockets
    }
    
    /**
     * 简化版：根据屏幕尺寸估算袋口位置
     */
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
        
        val cornerOffsetX = width * CORNER_POCKET_OFFSET_X
        val cornerOffsetY = height * CORNER_POCKET_OFFSET_Y
        val middleOffsetY = height * MIDDLE_POCKET_OFFSET
        
        val pocketRadius = minOf(width, height) * 0.04f
        
        return listOf(
            PocketInfo(PointF(left + cornerOffsetX, top + cornerOffsetY), 
                PocketType.TOP_LEFT, pocketRadius),
            PocketInfo(PointF(left + width / 2, top + middleOffsetY), 
                PocketType.TOP_MIDDLE, pocketRadius * 1.2f),
            PocketInfo(PointF(right - cornerOffsetX, top + cornerOffsetY), 
                PocketType.TOP_RIGHT, pocketRadius),
            PocketInfo(PointF(left + cornerOffsetX, bottom - cornerOffsetY), 
                PocketType.BOTTOM_LEFT, pocketRadius),
            PocketInfo(PointF(left + width / 2, bottom - middleOffsetY), 
                PocketType.BOTTOM_MIDDLE, pocketRadius * 1.2f),
            PocketInfo(PointF(right - cornerOffsetX, bottom - cornerOffsetY), 
                PocketType.BOTTOM_RIGHT, pocketRadius)
        )
    }
    
    /**
     * 获取袋口位置列表
     */
    fun getPocketPositions(pockets: List<PocketInfo>): List<PointF> {
        return pockets.map { it.position }
    }
    
    /**
     * 对顶点进行排序
     */
    private fun orderCorners(corners: Array<Point>): Array<Point> {
        if (corners.size != 4) return corners
        
        val centerX = corners.sumOf { it.x } / corners.size
        val centerY = corners.sumOf { it.y } / corners.size
        
        return corners.sortedBy { corner ->
            atan2(corner.y - centerY, corner.x - centerX)
        }.toTypedArray()
    }
}
