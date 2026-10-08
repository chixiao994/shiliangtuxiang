package com.vectorimage.app.potrace

import kotlin.math.sqrt

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
    var optimalPolygon: List<Int> = emptyList()
    var curveSegments: List<CurveSegment> = emptyList()
    var optimizedSegments: List<CurveSegment> = emptyList()
}

data class PotraceParams(
    val turdSize: Int = 8,                    // 过滤面积 < 该值的轮廓
    val turnPolicy: TurnPolicy = TurnPolicy.MINORITY,
    val alphaMax: Double = 1.0,
    val optimizeCurve: Boolean = true,
    val optTolerance: Double = 0.2,
    val minComponentSize: Int = 20,           // 过滤小于该面积的前景连通分量
    val maxHoleSize: Int = 30,                // 填充小于该面积的内部孔洞
    val rdpEpsilon: Double = 0.8,             // RDP 简化容差（像素）—— 调小可保留更多细节
    val cornerAngleDeg: Double = 60.0         // 转角超过此角度视为角点（保留锐角）
)

enum class TurnPolicy { BLACK, WHITE, LEFT, RIGHT, MINORITY, MAJORITY, RANDOM }
