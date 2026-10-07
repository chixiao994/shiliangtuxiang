package com.vectorimage.app.potrace

import kotlin.math.min

object CurveGenerator {

    fun generate(path: PotracePath, params: PotraceParams) {
        val pts = path.points
        val polygon = path.optimalPolygon
        val n = polygon.size
        if (n < 3) {
            path.curveSegments = emptyList()
            return
        }

        val dpts = polygon.map { DPoint(pts[it].x.toDouble(), pts[it].y.toDouble()) }
        val segments = mutableListOf<CurveSegment>()

        for (i in 0 until n) {
            val p0 = dpts[i]
            val p1 = dpts[(i + 1) % n]
            val dir = (p1 - p0).normalize()
            if (dir.length() < 1e-9) continue

            val prev = dpts[(i - 1 + n) % n]
            val next = dpts[(i + 2) % n]

            val v1 = (p0 - prev).normalize()
            val v2 = (p1 - p0).normalize()
            val v3 = (next - p1).normalize()

            val dot1 = v1.dot(v2)
            val dot2 = v2.dot(v3)

            val isCorner = dot1 > 0.5 || dot2 > 0.5

            if (isCorner) {
                segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(DPoint(0.0, 0.0), p0)))
            } else {
                val ctrl = p1
                val end = DPoint((p1.x + next.x) * 0.5, (p1.y + next.y) * 0.5)
                val alpha = computeAlpha(p0, ctrl, end)
                segments.add(CurveSegment(SegmentTag.CURVETO, arrayOf(p0, ctrl, end), alpha))
            }
        }
        path.curveSegments = segments
    }

    private fun computeAlpha(p0: DPoint, ctrl: DPoint, end: DPoint): Double {
        val d1 = (ctrl - p0).length()
        val d2 = (end - ctrl).length()
        if (d1 < 1e-9 || d2 < 1e-9) return 1.0
        return min(1.0, d1 / d2)
    }
}
