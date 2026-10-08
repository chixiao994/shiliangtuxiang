package com.vectorimage.app.potrace

import kotlin.math.cos

/**
 * 角点检测 + 中点法二次贝塞尔：
 * - 转角 > cornerAngleDeg 的顶点视为角点，用 L 直线保留锐角
 * - 非角点区域用中点法生成 Q 曲线，保证曲线平滑
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

        // ---------- 角点检测 ----------
        val isCorner = BooleanArray(n)
        val cosThr = cos(Math.toRadians(params.cornerAngleDeg))
        for (i in 0 until n) {
            val prev = vertices[(i - 1 + n) % n]
            val cur = vertices[i]
            val next = vertices[(i + 1) % n]
            val v1 = cur - prev
            val v2 = next - cur
            val l1 = v1.length()
            val l2 = v2.length()
            if (l1 < 1e-6 || l2 < 1e-6) { isCorner[i] = true; continue }
            val cosA = v1.dot(v2) / (l1 * l2)
            if (cosA < cosThr) isCorner[i] = true
        }

        val segments = mutableListOf<CurveSegment>()

        // ---------- 收集角点 ----------
        val cornerIdx = ArrayList<Int>()
        for (i in 0 until n) if (isCorner[i]) cornerIdx.add(i)

        if (cornerIdx.isEmpty()) {
            // 全部是平滑段 → 纯中点法
            val firstMid = mid(vertices[n - 1], vertices[0])
            segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(firstMid, firstMid)))
            for (i in 0 until n) {
                val end = mid(vertices[i], vertices[(i + 1) % n])
                segments.add(
                    CurveSegment(
                        SegmentTag.CURVETO,
                        arrayOf(mid(vertices[(i - 1 + n) % n], vertices[i]), vertices[i], end)
                    )
                )
            }
            path.curveSegments = segments
            return
        }

        // ---------- 从第一个角点出发，逐段处理 ----------
        val startIdx = cornerIdx[0]
        // 起点标记段（只用于 MOVE TO，不绘制）
        segments.add(
            CurveSegment(
                SegmentTag.CORNER,
                arrayOf(vertices[startIdx], vertices[startIdx])
            )
        )

        var i = startIdx
        var remaining = n
        while (remaining > 0) {
            // 找下一个角点 j
            var steps = 1
            var j = (i + 1) % n
            while (!isCorner[j] && steps < n) {
                j = (j + 1) % n
                steps++
            }

            // P = [vertices[i], vertices[i+1], ..., vertices[j]]
            val P = ArrayList<DPoint>(steps + 1)
            for (s in 0..steps) P.add(vertices[(i + s) % n])

            if (steps == 1) {
                // 相邻角点：直接 L
                segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(P[0], P[1])))
            } else {
                // 中点法：角点 → 曲线 → 角点
                val M = ArrayList<DPoint>(steps)
                for (s in 0 until steps) M.add(mid(P[s], P[s + 1]))

                // 从角点 P0 直线到第一个中点 M0
                segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(P[0], M[0])))
                // M0 → M1 → ... → M_{k-1} 用 Q，控制点 P1..P_{k-1}
                for (s in 0 until steps - 1) {
                    segments.add(
                        CurveSegment(
                            SegmentTag.CURVETO,
                            arrayOf(M[s], P[s + 1], M[s + 1])
                        )
                    )
                }
                // 从最后一个中点 M_{k-1} 直线到角点 Pk
                segments.add(
                    CurveSegment(
                        SegmentTag.CORNER,
                        arrayOf(M[steps - 1], P[steps])
                    )
                )
            }

            remaining -= steps
            if (j == startIdx) break
            i = j
        }

        path.curveSegments = segments
    }

    private fun mid(a: DPoint, b: DPoint) =
        DPoint((a.x + b.x) * 0.5, (a.y + b.y) * 0.5)
}
