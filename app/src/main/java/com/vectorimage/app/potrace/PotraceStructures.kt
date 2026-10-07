package com.vectorimage.app.potrace

import kotlin.math.*

data class IntPoint(val x: Int, val y: Int)

data class DPoint(val x: Double, val y: Double) {
    operator fun minus(o: DPoint) = DPoint(x - o.x, y - o.y)
    operator fun plus(o: DPoint) = DPoint(x + o.x, y + o.y)
    operator fun times(k: Double) = DPoint(x * k, y * k)
    fun dot(o: DPoint) = x * o.x + y * o.y
    fun cross(o: DPoint) = x * o.y - y * o.x
    fun length() = sqrt(x * x + y * y)
    fun distanceTo(o: DPoint) = (this - o).length()
    fun normalize(): DPoint {
        val l = length()
        return if (l < 1e-9) DPoint(0.0, 0.0) else DPoint(x / l, y / l)
    }
}

enum class SegmentTag { CORNER, CURVETO }

data class CurveSegment(
    val tag: SegmentTag,
    val c: Array<DPoint>,
    val alpha: Double = 1.0
)

class PotracePath(val points: List<IntPoint>) {
    var area = 0L
    var lon: IntArray = IntArray(0)
    var sums: List<Sum> = emptyList()
    var optimalPolygon: List<Int> = emptyList()
    var curveSegments: List<CurveSegment> = emptyList()
    var optimizedSegments: List<CurveSegment> = emptyList()
}

data class Sum(
    val x: Double, val y: Double,
    val x2: Double, val xy: Double, val y2: Double
) {
    operator fun plus(o: Sum) = Sum(x + o.x, y + o.y, x2 + o.x2, xy + o.xy, y2 + o.y2)
    operator fun minus(o: Sum) = Sum(x - o.x, y - o.y, x2 - o.x2, xy - o.xy, y2 - o.y2)
}

data class PotraceParams(
    val turdSize: Int = 6,                 // 过滤小轮廓（面积 < turdSize 的丢弃）
    val turnPolicy: TurnPolicy = TurnPolicy.MINORITY,
    val alphaMax: Double = 1.0,
    val optimizeCurve: Boolean = true,
    val optTolerance: Double = 0.2,
    val morphCloseRadius: Int = 2,         // 形态学闭运算半径（填充笔画内部小洞）
    val polygonEpsilon: Double = 1.2       // 多边形拟合最大允许的平均垂直距离（像素）
)

enum class TurnPolicy { BLACK, WHITE, LEFT, RIGHT, MINORITY, MAJORITY, RANDOM }
