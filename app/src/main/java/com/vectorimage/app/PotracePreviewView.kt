package com.vectorimage.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.vectorimage.app.potrace.CurveSegment
import com.vectorimage.app.potrace.PotracePath
import com.vectorimage.app.potrace.SegmentTag

class PotracePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var paths: List<PotracePath> = emptyList()
    private var imgW = 1
    private var imgH = 1
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
    }
    private val bgPaint = Paint().apply { color = 0xFFFFFFFF.toInt() }

    fun setPaths(paths: List<PotracePath>, w: Int, h: Int) {
        this.paths = paths
        this.imgW = w.coerceAtLeast(1)
        this.imgH = h.coerceAtLeast(1)
        invalidate()
    }

    fun clear() {
        paths = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (paths.isEmpty()) return

        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = minOf(vw / imgW, vh / imgH)
        val dx = (vw - imgW * scale) / 2f
        val dy = (vh - imgH * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // 所有轮廓合并到一个 Path 里，用 EVEN_ODD 规则填充，孔洞会被正确扣除
        val combined = Path()
        combined.fillType = Path.FillType.EVEN_ODD

        for (p in paths) {
            val segments = if (p.optimizedSegments.isNotEmpty()) p.optimizedSegments
            else p.curveSegments
            if (segments.isEmpty()) continue

            val first = segments.first()
            val start = if (first.tag == SegmentTag.CORNER) first.c[1] else first.c[0]
            combined.moveTo(start.x.toFloat(), start.y.toFloat())

            for (seg in segments) {
                when (seg.tag) {
                    SegmentTag.CORNER -> {
                        val v = seg.c[1]
                        combined.lineTo(v.x.toFloat(), v.y.toFloat())
                    }
                    SegmentTag.CURVETO -> {
                        val ctrl = seg.c[1]
                        val end = seg.c[2]
                        combined.quadTo(
                            ctrl.x.toFloat(), ctrl.y.toFloat(),
                            end.x.toFloat(), end.y.toFloat()
                        )
                    }
                }
            }
            combined.close()
        }

        canvas.drawPath(combined, fillPaint)
        canvas.restore()
    }
}
