package com.vectorimage.app.potrace

/**
 * 中点法生成二次贝塞尔曲线：
 * 每条边的中点为曲线端点，原多边形顶点为控制点。
 * 这样曲线会平滑地通过所有中点，控制点为原顶点。
 */
object CurveGenerator {

    fun generate(path: PotracePath, params: PotraceParams) {
        val pts = path.points
        val polygon = path.optimalPolygon
        val n = polygon.size
        if (n < 3) {
            path.curveSegments = emptyList()
            return
        }

        val vertices = polygon.map {
            DPoint(pts[it].x.toDouble(), pts[it].y.toDouble())
        }

        val midpoints = ArrayList<DPoint>(n)
        for (i in 0 until n) {
            val p = vertices[i]
            val q = vertices[(i + 1) % n]
            midpoints.add(DPoint((p.x + q.x) * 0.5, (p.y + q.y) * 0.5))
        }

        val segments = mutableListOf<CurveSegment>()
        for (i in 0 until n) {
            val start = midpoints[(i - 1 + n) % n]
            val ctrl = vertices[i]
            val end = midpoints[i]
            segments.add(CurveSegment(SegmentTag.CURVETO, arrayOf(start, ctrl, end)))
        }
        path.curveSegments = segments
    }
}
