package com.vectorimage.app.potrace

import kotlin.math.abs

object PathDecomposer {

    private val DIR_X = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DIR_Y = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    fun decompose(
        binary: BooleanArray,
        w: Int, h: Int,
        params: PotraceParams
    ): List<PotracePath> {
        val visited = BooleanArray(w * h)
        val paths = mutableListOf<PotracePath>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!binary[idx] || visited[idx]) continue
                // 只从每段前景的最左端开始
                if (x > 0 && binary[y * w + x - 1]) continue

                val path = tracePath(binary, w, h, x, y, visited, params)
                if (path != null) paths.add(path)
            }
        }
        return paths
    }

    /**
     * Moore 邻域轮廓追踪（加入完整边界检查，避免越界崩溃）。
     * - backDir：上一个位置相对当前位置的方向
     * - 从 (backDir + 1) 开始顺时针扫描 8 个方向，找到第一个前景像素即为下一个位置
     */
    private fun tracePath(
        binary: BooleanArray, w: Int, h: Int,
        startX: Int, startY: Int,
        visited: BooleanArray,
        params: PotraceParams
    ): PotracePath? {
        val points = mutableListOf<IntPoint>()
        var cx = startX
        var cy = startY
        var backDir = 4   // 初始：假设我们从左侧进入
        var area = 0L
        var guard = 0
        val maxSteps = w * h * 8

        do {
            points.add(IntPoint(cx, cy))
            visited[cy * w + cx] = true

            var found = false
            for (k in 1..8) {
                val d = (backDir + k) % 8
                val nx = cx + DIR_X[d]
                val ny = cy + DIR_Y[d]
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                if (!binary[ny * w + nx]) continue

                // 计算面积
                area += cx.toLong() * ny - nx.toLong() * cy
                // 新位置的"回退方向"是 d 的反方向
                backDir = (d + 4) % 8
                cx = nx
                cy = ny
                found = true
                break
            }
            if (!found) break
            guard++
        } while (!(cx == startX && cy == startY) && guard < maxSteps)

        if (points.size < 4) return null
        if (abs(area) < params.turdSize) return null

        val path = PotracePath(points)
        path.area = area
        return path
    }
}
