package com.vectorimage.app.potrace

object PolygonFitter {

    fun fit(path: PotracePath, params: PotraceParams) {
        val pts = path.points
        val n = pts.size
        if (n < 3) { path.optimalPolygon = emptyList(); return }
        path.sums = computePrefixSums(pts)
        path.optimalPolygon = findBestPolygon(pts, path.sums, params.polygonEpsilon)
    }

    /** 前缀和：x, y, x², xy, y² */
    private fun computePrefixSums(pts: List<IntPoint>): List<Sum> {
        val n = pts.size
        val sums = ArrayList<Sum>(n + 1)
        var sx = 0.0; var sy = 0.0; var sx2 = 0.0; var sxy = 0.0; var sy2 = 0.0
        sums.add(Sum(0.0, 0.0, 0.0, 0.0, 0.0))
        for (p in pts) {
            sx += p.x; sy += p.y
            sx2 += p.x.toDouble() * p.x
            sxy += p.x.toDouble() * p.y
            sy2 += p.y.toDouble() * p.y
            sums.add(Sum(sx, sy, sx2, sxy, sy2))
        }
        return sums
    }

    /**
     * Potrace 风格的惩罚函数：
     * penalty(i, j) = (1/k) * Σ (p 到直线 (i, j) 的垂直距离)²
     * 其中 k 是区间 [i, j] 内的点数。
     *
     * 利用 Σdx²、Σdy²、Σdx·dy 三个和，避免逐点遍历。
     */
    private fun penalty(pts: List<IntPoint>, sums: List<Sum>, i: Int, j: Int): Double {
        val n = pts.size
        val k = (j - i + n) % n
        if (k < 2) return 0.0

        val s = sums[j + 1] - sums[i]   // 区间 [i, j] 内所有点的和
        val kd = k.toDouble()

        val x0 = pts[i].x.toDouble()
        val y0 = pts[i].y.toDouble()
        val x1 = pts[j].x.toDouble()
        val y1 = pts[j].y.toDouble()

        val dx = x1 - x0
        val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-9) return 0.0

        // Σ (p.x - x0)²
        val sumDx2 = s.x2 - 2.0 * x0 * s.x + kd * x0 * x0
        // Σ (p.y - y0)²
        val sumDy2 = s.y2 - 2.0 * y0 * s.y + kd * y0 * y0
        // Σ (p.x - x0)(p.y - y0)
        val sumDxy = s.xy - x0 * s.y - y0 * s.x + kd * x0 * y0

        // Σ (p 到直线的有向距离 × |line|)² = dx²·Σdy² - 2·dx·dy·Σdxdy + dy²·Σdx²
        val sumD2 = dx * dx * sumDy2 - 2.0 * dx * dy * sumDxy + dy * dy * sumDx2

        // 平均垂直距离平方 = sumD2 / (k * len2)
        return sumD2 / (kd * len2)
    }

    /**
     * 从最左点开始，每次找最远的可行点：
     * 从 current 向前扩张，直到 penalty 超过 maxPenalty 为止。
     */
    private fun findBestPolygon(
        pts: List<IntPoint>, sums: List<Sum>, maxPenalty: Double
    ): List<Int> {
        val n = pts.size
        if (n < 3) return emptyList()

        var start = 0
        for (i in 1 until n) if (pts[i].x < pts[start].x) start = i

        val polygon = mutableListOf<Int>()
        var current = start
        polygon.add(current)

        var safety = 0
        while (safety < n) {
            safety++

            var bestNext = -1
            var next = (current + 2) % n
            var iter = 0
            while (next != start && iter < n) {
                iter++
                val p = penalty(pts, sums, current, next)
                if (p <= maxPenalty) {
                    bestNext = next   // 还能连，继续尝试更远
                } else {
                    break             // 太远，回退到上一个可行点
                }
                next = (next + 1) % n
            }

            // 太近也连不上（几乎不可能，防御性编程）
            if (bestNext == -1) bestNext = (current + 1) % n

            if (bestNext == start) break
            polygon.add(bestNext)
            current = bestNext
        }

        return polygon
    }
}
