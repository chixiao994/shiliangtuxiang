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
                if (x > 0 && binary[y * w + x - 1]) continue

                val path = tracePath(binary, w, h, x, y, visited, params)
                if (path != null) paths.add(path)
            }
        }
        return paths
    }

    private fun tracePath(
        binary: BooleanArray, w: Int, h: Int,
        startX: Int, startY: Int,
        visited: BooleanArray,
        params: PotraceParams
    ): PotracePath? {
        val points = mutableListOf<IntPoint>()
        var cx = startX
        var cy = startY
        var dir = 6
        var area = 0L
        var guard = 0
        val maxSteps = w * h * 4

        do {
            points.add(IntPoint(cx, cy))
            visited[cy * w + cx] = true
            val nx = cx + DIR_X[dir]
            val ny = cy + DIR_Y[dir]
            area += cx.toLong() * ny - nx.toLong() * cy
            cx = nx
            cy = ny
            if (cx == startX && cy == startY) break
            dir = determineNextDirection(binary, w, h, cx, cy, dir, params.turnPolicy)
            guard++
        } while (guard < maxSteps)

        if (points.size < 4) return null
        if (abs(area) < params.turdSize) return null

        val path = PotracePath(points)
        path.area = area
        return path
    }

    private fun determineNextDirection(
        binary: BooleanArray, w: Int, h: Int,
        cx: Int, cy: Int, currentDir: Int,
        policy: TurnPolicy
    ): Int {
        val frontDir = currentDir
        val leftDir = (currentDir + 6) % 8
        val rightDir = (currentDir + 2) % 8

        val front = getPixel(binary, w, h, cx, cy, frontDir)
        val left = getPixel(binary, w, h, cx, cy, leftDir)
        val right = getPixel(binary, w, h, cx, cy, rightDir)

        if (front && !left && !right) return frontDir
        if (!front && left && !right) return leftDir
        if (!front && !left && right) return rightDir
        if (front && left && !right) return if (policy == TurnPolicy.LEFT) leftDir else frontDir
        if (front && !left && right) return if (policy == TurnPolicy.RIGHT) rightDir else frontDir
        if (!front && left && right) return if (policy == TurnPolicy.LEFT) leftDir else rightDir
        if (front && left && right) return frontDir
        return (currentDir + 4) % 8
    }

    private fun getPixel(binary: BooleanArray, w: Int, h: Int, x: Int, y: Int, dir: Int): Boolean {
        val nx = x + DIR_X[dir]
        val ny = y + DIR_Y[dir]
        return nx in 0 until w && ny in 0 until h && binary[ny * w + nx]
    }
}
