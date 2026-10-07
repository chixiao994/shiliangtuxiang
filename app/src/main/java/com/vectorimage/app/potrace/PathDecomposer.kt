package com.vectorimage.app.potrace

object PathDecomposer {

    // 方向: 0=东(+1,0), 1=南(0,+1), 2=西(-1,0), 3=北(0,-1)
    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)

    fun decompose(
        binary: BooleanArray,
        w: Int, h: Int,
        params: PotraceParams
    ): List<PotracePath> {
        val paths = mutableListOf<PotracePath>()

        // 1. 外轮廓：从每个前景连通分量的最左上像素开始
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!binary[idx]) continue
                if (x > 0 && binary[y * w + x - 1]) continue
                if (y > 0 && binary[(y - 1) * w + x]) continue

                val points = traceContour(binary, w, h, x, y, isHole = false)
                if (points != null && points.size >= 3) {
                    val path = PotracePath(points)
                    path.area = computeArea(points)
                    paths.add(path)
                }
            }
        }

        // 2. 孔洞轮廓：从每个背景连通分量的最左上像素开始
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (binary[idx]) continue
                if (binary[y * w + x - 1]) continue
                if (binary[(y - 1) * w + x]) continue

                val points = traceContour(binary, w, h, x, y, isHole = true)
                if (points != null && points.size >= 3) {
                    val path = PotracePath(points)
                    path.area = computeArea(points)
                    paths.add(path)
                }
            }
        }

        return paths
    }

    private fun traceContour(
        binary: BooleanArray, w: Int, h: Int,
        startX: Int, startY: Int, isHole: Boolean
    ): List<IntPoint>? {
        val points = mutableListOf<IntPoint>()
        var x = startX
        var y = startY
        var dir = 0
        val startDir = 0
        val maxSteps = (w + h) * 4 + 1024
        var steps = 0

        do {
            points.add(IntPoint(x, y))

            // 优先级：直行 > 左转 > 右转
            val tryDirs = intArrayOf(dir, (dir + 3) % 4, (dir + 1) % 4)
            var moved = false
            for (d in tryDirs) {
                if (canMove(binary, w, h, x, y, d, isHole)) {
                    x += DX[d]
                    y += DY[d]
                    dir = d
                    moved = true
                    break
                }
            }
            if (!moved) return null

            steps++
            if (steps > maxSteps) return null
        } while (!(x == startX && y == startY && dir == startDir))

        return if (points.size >= 3) points else null
    }

    /**
     * 检查从格点 (x, y) 沿方向 d 移动是否合法。
     * 移动后的格点为 (x+DX[d], y+DY[d])。
     *
     * 移动路径两侧的像素（按行进方向的左手/右手）：
     *   d=0（东）: 左=(x, y-1), 右=(x, y)
     *   d=1（南）: 左=(x, y),   右=(x-1, y)
     *   d=2（西）: 左=(x-1, y), 右=(x-1, y-1)
     *   d=3（北）: 左=(x-1, y-1), 右=(x, y-1)
     */
    private fun canMove(
        binary: BooleanArray, w: Int, h: Int,
        x: Int, y: Int, d: Int, isHole: Boolean
    ): Boolean {
        val nx = x + DX[d]
        val ny = y + DY[d]
        if (nx < 0 || nx > w || ny < 0 || ny > h) return false

        val lx: Int; val ly: Int; val rx: Int; val ry: Int
        when (d) {
            0 -> { lx = x; ly = y - 1; rx = x; ry = y }
            1 -> { lx = x; ly = y; rx = x - 1; ry = y }
            2 -> { lx = x - 1; ly = y; rx = x - 1; ry = y - 1 }
            3 -> { lx = x - 1; ly = y - 1; rx = x; ry = y - 1 }
            else -> return false
        }

        val leftFg = lx in 0 until w && ly in 0 until h && binary[ly * w + lx]
        val rightFg = rx in 0 until w && ry in 0 until h && binary[ry * w + rx]

        return if (isHole) {
            // 孔洞: 左=前景, 右=背景
            leftFg && !rightFg
        } else {
            // 外轮廓: 左=背景, 右=前景
            !leftFg && rightFg
        }
    }

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
