package com.vectorimage.app.potrace

import kotlin.math.abs

object PolygonFitter {

    fun fit(path: PotracePath) {
        val pts = path.points
        val n = pts.size
        if (n < 3) return
        path.lon = computeLongestLines(pts)
        path.sums = computePrefixSums(pts)
        path.optimalPolygon = findOptimalPolygon(path)
    }

    private fun computeLongestLines(pts: List<IntPoint>): IntArray {
        val n = pts.size
        val lon = IntArray(n)
        for (i in 0 until n) {
            var j = (i + 1) % n
            val dir = IntPoint(pts[j].x - pts[i].x, pts[j].y - pts[i].y)
            var count = 1
            while (j != i) {
                val next = (j + 1) % n
                val ndir = IntPoint(pts[next].x - pts[j].x, pts[next].y - pts[j].y)
                if (ndir.x * dir.y - ndir.y * dir.x != 0) break
                j = next
                count++
                if (j == i) break
            }
            lon[i] = count
        }
        return lon
    }

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

    private fun penalty(pts: List<IntPoint>, sums: List<Sum>, i: Int, j: Int): Double {
        val n = pts.size
        val k = (j - i + n) % n
        if (k < 2) return 0.0
        val sum = sums[j + 1] - sums[i]
        val kd = k.toDouble()
        val x0 = pts[i].x.toDouble()
        val y0 = pts[i].y.toDouble()
        val x1 = pts[j].x.toDouble()
        val y1 = pts[j].y.toDouble()
        val dx = x1 - x0
        val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-9) return 0.0
        val ex = sum.x - kd * x0
        val ey = sum.y - kd * y0
        val cross = dx * ey - dy * ex
        return cross * cross / len2
    }

    private fun findOptimalPolygon(path: PotracePath): List<Int> {
        val pts = path.points
        val n = pts.size
        val sums = path.sums
        if (n < 3) return emptyList()

        // 起点：最左边的点
        var start = 0
        for (i in 1 until n) if (pts[i].x < pts[start].x) start = i

        val polygon = mutableListOf<Int>()
        var current = start
        polygon.add(current)

        var iter = 0
        val maxIter = n + 4  // 防止死循环

        while (iter < maxIter) {
            iter++

            var bestNext = -1
            var bestPenalty = Double.MAX_VALUE

            var next = (current + 2) % n
            var stepCount = 0
            while (next != current && stepCount < n) {
                stepCount++
                val p = penalty(pts, sums, current, next)
                if (p < bestPenalty) {
                    bestPenalty = p
                    bestNext = next
                }
                next = (next + 1) % n
            }

            if (bestNext == -1 || bestNext == start) break
            polygon.add(bestNext)
            current = bestNext
        }

        return polygon
    }
}
