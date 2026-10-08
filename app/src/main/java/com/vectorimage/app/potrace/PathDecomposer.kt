package com.vectorimage.app.potrace

import kotlin.math.abs

/**
 * 边界边 + 环追踪：
 * 1. 枚举每个前景像素的 4 条边界（邻居是背景的边），每条边有方向
 * 2. 按「起点格点」把边分组
 * 3. 从任意未访问边出发，用「右转优先」规则连接成环
 * 这样就能得到精确的像素级轮廓，不会跨笔画乱跳。
 */
object PathDecomposer {

    private data class Edge(val x: Int, val y: Int, val dir: Int, val id: Int) {
        // dir: 0=E, 1=S, 2=W, 3=N
        val nextX: Int get() = when (dir) {
            0 -> x + 1
            1 -> x
            2 -> x - 1
            else -> x
        }
        val nextY: Int get() = when (dir) {
            0 -> y
            1 -> y + 1
            2 -> y
            else -> y - 1
        }
    }

    fun decompose(
        binary: BooleanArray,
        w: Int, h: Int,
        params: PotraceParams
    ): List<PotracePath> {
        val edges = ArrayList<Edge>()
        var id = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!binary[y * w + x]) continue

                // 顶边：上方是背景 → 边从 (x, y) 到 (x+1, y)，方向 E
                if (y == 0 || !binary[(y - 1) * w + x]) {
                    edges.add(Edge(x, y, 0, id++))
                }
                // 右边：右方是背景 → 边从 (x+1, y) 到 (x+1, y+1)，方向 S
                if (x == w - 1 || !binary[y * w + x + 1]) {
                    edges.add(Edge(x + 1, y, 1, id++))
                }
                // 底边：下方是背景 → 边从 (x+1, y+1) 到 (x, y+1)，方向 W
                if (y == h - 1 || !binary[(y + 1) * w + x]) {
                    edges.add(Edge(x + 1, y + 1, 2, id++))
                }
                // 左边：左方是背景 → 边从 (x, y+1) 到 (x, y)，方向 N
                if (x == 0 || !binary[y * w + x - 1]) {
                    edges.add(Edge(x, y + 1, 3, id++))
                }
            }
        }

        if (edges.isEmpty()) return emptyList()

        val wPlus = w + 1
        val outByPoint = HashMap<Long, MutableList<Int>>()
        for (e in edges) {
            val key = e.y.toLong() * wPlus + e.x
            outByPoint.getOrPut(key) { mutableListOf() }.add(e.id)
        }

        val visited = BooleanArray(edges.size)
        val paths = mutableListOf<PotracePath>()

        for (startId in edges.indices) {
            if (visited[startId]) continue
            val cycle = traceCycle(edges, outByPoint, visited, startId, wPlus)
            if (cycle != null && cycle.size >= 4) {
                val path = PotracePath(cycle)
                path.area = computeArea(cycle)
                paths.add(path)
            }
        }

        return paths.filter { abs(it.area) >= params.turdSize.toLong() }
    }

    private fun traceCycle(
        edges: List<Edge>,
        outByPoint: Map<Long, List<Int>>,
        visited: BooleanArray,
        startId: Int,
        wPlus: Int
    ): List<IntPoint>? {
        val points = mutableListOf<IntPoint>()
        var curId = startId
        var dirIn = -1
        var guard = 0
        val maxSteps = edges.size * 2 + 16

        while (guard < maxSteps) {
            guard++
            val cur = edges[curId]
            points.add(IntPoint(cur.x, cur.y))
            visited[curId] = true

            val nx = cur.nextX
            val ny = cur.nextY
            val nextKey = ny.toLong() * wPlus + nx
            val candidates = outByPoint[nextKey] ?: break

            // 方向优先级：右转 > 直行 > 左转 > 掉头
            val dirOrder = if (dirIn < 0) {
                intArrayOf(0, 1, 2, 3)
            } else {
                intArrayOf(
                    (dirIn + 1) % 4,
                    dirIn,
                    (dirIn + 3) % 4,
                    (dirIn + 2) % 4
                )
            }

            var nextId = -1
            outer@ for (d in dirOrder) {
                for (cid in candidates) {
                    if (visited[cid]) continue
                    if (edges[cid].dir != d) continue
                    nextId = cid
                    break@outer
                }
            }

            if (nextId < 0) break

            curId = nextId
            dirIn = edges[curId].dir

            if (curId == startId) break
        }

        // 保证闭合
        if (points.isNotEmpty()) {
            val first = points.first()
            val last = points.last()
            if (first.x != last.x || first.y != last.y) {
                points.add(IntPoint(first.x, first.y))
            }
        }

        return points
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
