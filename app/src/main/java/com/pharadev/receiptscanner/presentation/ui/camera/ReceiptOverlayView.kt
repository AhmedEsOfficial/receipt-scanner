package com.pharadev.receiptscanner.presentation.ui.camera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.pharadev.receiptscanner.R

class ReceiptOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 140 // Semi-transparent overlay
        style = Paint.Style.FILL
    }
    
    private val cornerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    
    private val cornerLength = 60f
    private var receiptRect = RectF()
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateReceiptBounds(w, h)
    }
    
    private fun calculateReceiptBounds(width: Int, height: Int) {
        // Create a receipt-shaped rectangle in the center
        val aspectRatio = 0.7f // Typical receipt aspect ratio (width/height)
        val margin = width * 0.1f // 10% margin from edges
        
        val rectWidth = width - (2 * margin)
        val rectHeight = rectWidth / aspectRatio
        
        // Center vertically, but slightly towards top for better UX
        val topOffset = (height * 0.3f)
        val left = margin
        val top = topOffset
        val right = left + rectWidth
        val bottom = top + rectHeight
        
        receiptRect = RectF(left, top, right, bottom)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOverlay(canvas)
    }
    
    private fun drawOverlay(canvas: Canvas) {
        // Draw semi-transparent overlay everywhere except the receipt area
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(receiptRect, 16f, 16f, Path.Direction.CCW)
        }
        
        canvas.drawPath(path, overlayPaint)
        
        // Draw dashed frame around receipt area
        val framePath = Path().apply {
            addRoundRect(receiptRect, 16f, 16f, Path.Direction.CW)
        }
        canvas.drawPath(framePath, framePaint)
        
        // Draw corner indicators
        drawCorners(canvas)
        
        // Draw guide text
        drawGuideText(canvas)
    }
    
    private fun drawCorners(canvas: Canvas) {
        val corners = listOf(
            // Top-left corner
            Pair(receiptRect.left, receiptRect.top),
            // Top-right corner  
            Pair(receiptRect.right, receiptRect.top),
            // Bottom-left corner
            Pair(receiptRect.left, receiptRect.bottom),
            // Bottom-right corner
            Pair(receiptRect.right, receiptRect.bottom)
        )
        
        corners.forEachIndexed { index, (x, y) ->
            when (index) {
                0 -> { // Top-left
                    canvas.drawLine(x, y, x + cornerLength, y, cornerPaint)
                    canvas.drawLine(x, y, x, y + cornerLength, cornerPaint)
                }
                1 -> { // Top-right
                    canvas.drawLine(x, y, x - cornerLength, y, cornerPaint)
                    canvas.drawLine(x, y, x, y + cornerLength, cornerPaint)
                }
                2 -> { // Bottom-left
                    canvas.drawLine(x, y, x + cornerLength, y, cornerPaint)
                    canvas.drawLine(x, y, x, y - cornerLength, cornerPaint)
                }
                3 -> { // Bottom-right
                    canvas.drawLine(x, y, x - cornerLength, y, cornerPaint)
                    canvas.drawLine(x, y, x, y - cornerLength, cornerPaint)
                }
            }
        }
    }
    
    private fun drawGuideText(canvas: Canvas) {
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        
        val guideText = "Position receipt within frame"
        val textBounds = Rect()
        textPaint.getTextBounds(guideText, 0, guideText.length, textBounds)
        
        val centerX = width / 2f
        val textY = receiptRect.top - textBounds.height() - 32f
        
        canvas.drawText(guideText, centerX, textY, textPaint)
    }
    
    fun getReceiptBounds(): RectF = receiptRect
    
    // Animation for successful capture feedback
    fun animateCapture() {
        animate()
            .alpha(0.3f)
            .setDuration(150)
            .withEndAction {
                animate()
                    .alpha(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }
}