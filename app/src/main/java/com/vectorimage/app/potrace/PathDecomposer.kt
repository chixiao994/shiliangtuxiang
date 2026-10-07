package com.vectorimage.app.potrace

object PathDecomposer {

    // 方向: 0=E, 1=S, 2=W, 3=N
    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)

    fun decompose(
        binary: BooleanArray,
        w: Int, h: Int,
        params: PotraceParams
    ): List<PotracePath> {
        val visited = BooleanArray(w * h)
        val paths = mutableListOf<PotracePath>()

        // 1. 外轮廓：从每个前景连通分量的最左上点开始
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!binary[idx] || visited[idx]) continue
                val upFg = y > 0 && binary[(y - 1) * w + x]
                val leftFg = x > 0 && binary[y * w + x - 1]
                if (upFg || leftFg) continue

                val path = tracePath(binary, w, h, x, y, visited, traceHole = false)
                if (path != null && path.points.size >= 4) paths.add(path)
            }
        }

        // 2. 孔洞轮廓：从每个背景连通分量的最左上点开始
        val holeVisited = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (binary[idx] || holeVisited[idx]) continue
                val upBg = y == 0 || !binary[(y - 1) * w + x]
                val leftBg = x == 0 || !binary[y * w + x - 1]
                if (!upBg || !leftBg) continue
                // 排除图像边界上的背景（不是孔洞）
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) continue

                val path = tracePath(binary, w, h, x, y, holeVisited, traceHole = true)
                if (path != null && path.points.size >= 4) paths.add(path)
            }
        }

        return paths
    }

    /**
     * 角点追踪：沿像素边界走，每一步记录一个角点 (x, y)。
     * traceHole=true 时追踪的是「背景区域」的轮廓（即孔洞）。
     */
    private fun tracePath(
        binary: BooleanArray, w: Int, h: Int,
        startX: Int, startY: Int,
        visited: BooleanArray,
        traceHole: Boolean
    ): PotracePath? {
        val points = mutableListOf<IntPoint>()
        var x = startX
        var y = startY
        var dir = 0
        val maxSteps = (w * h * 8).coerceAtLeast(1024)
        var guard = 0

        points.add(IntPoint(x, y))
        if (traceHole) visited[y * w + x] = true

        while (guard < maxSteps) {
            guard++

            // 优先级：直行 > 左转 > 右转
            val candidates = intArrayOf(dir, (dir + 3) % 4, (dir + 1) % 4)
            var moved = false
            for (d in candidates) {
                if (canMove(binary, w, h, x, y, d, traceHole)) {
                    x += DX[d]
                    y += DY[d]
                    dir = d
                    moved = true
                    break
                }
            }
            if (!moved) break

            if (x == startX && y == startY) break

            points.add(IntPoint(x, y))
            if (!traceHole) {
                if (x in 0 until w && y in 0 until h) {
                    // 只标记起点像素，避免把整个连通分量都标记（因为轮廓可能穿过多个像素）
                }
            }
        }

        if (points.size < 4) return null
        val path = PotracePath(points)
        path.area = computeArea(points)
        return path
    }

    /**
     * 检查沿方向 d 从 (x, y) 到下一个角点是否在边界上。
     * traceHole=true 时边界判定相反（前景在右侧）。
     */
    private fun canMove(
        binary: BooleanArray, w: Int, h: Int,
        x: Int, y: Int, d: Int, traceHole: Boolean
    ): Boolean {
        val s1x: Int; val s1y: Int; val s2x: Int; val s2y: Int
        when (d) {
            0 -> { s1x = x; s1y = y - 1; s2x = x; s2y = y }
            1 -> { s1x = x - 1; s1y = y; s2x = x; s2y = y }
            2 -> { s1x = x - 1; s1y = y - 1; s2x = x - 1; s2y = y }
            3 -> { s1x = x - 1; s1y = y - 1; s2x = x; s2y = y - 1 }
            else -> return false
        }
        val fg1 = inBounds(s1x, s1y, w, h) && binary[s1y * w + s1x]
        val fg2 = inBounds(s2x, s2y, w, h) && binary[s2y * w + s2x]
        return if (traceHole) {
            // 孔洞：背景在左，前景在右
            fg1 == fg2 || !(fg1 || fg2)
                .let { false } // 这里简化为仍然是一前景一背景
                .or(fg1 != fg2)
        } else {
            fg1 != fg2
        }
    }

    private fun inBounds(x: Int, y: Int, w: Int, h: Int) =
        x in 0 until w && y in 0 until h

    private fun computeArea(points: List<IntPoint>): Long {
        var area = 0L
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            area += p.x.toLong() * q.y - q.x.toLong() * p.y
        }
        return area / 2
    }
}
