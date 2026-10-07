package com.vectorimage.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 将 Vectorizer 输出的矩形列表绘制到 Canvas 上，作为 SVG 预览。
 */
class SvgPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rects: List<Vectorizer.Rect> = emptyList()
    private var imageW = 1
    private var imageH = 1

    private val paint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
    }

    fun setRects(rects: List<Vectorizer.Rect>, w: Int, h: Int) {
        this.rects = rects
        this.imageW = w.coerceAtLeast(1)
        this.imageH = h.coerceAtLeast(1)
        invalidate()
    }

    fun clear() {
        rects = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (rects.isEmpty()) return

        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = minOf(vw / imageW, vh / imageH)
        val dx = (vw - imageW * scale) / 2f
        val dy = (vh - imageH * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        for (r in rects) {
            paint.color = r.color
            canvas.drawRect(
                r.x.toFloat(),
                r.y.toFloat(),
                (r.x + r.w).toFloat(),
                (r.y + r.h).toFloat(),
                paint
            )
        }
        canvas.restore()
    }
}
