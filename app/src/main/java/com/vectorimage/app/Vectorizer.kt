package com.vectorimage.app

import android.graphics.Bitmap

/**
 * 图像矢量化器：
 *  1. 缩放位图到最大 128 像素边长
 *  2. 颜色量化（按高位比特分箱 + 取出现频率最高的 N 个颜色）
 *  3. 每个像素映射到最近的调色板颜色
 *  4. 贪心合并同色相邻像素为矩形
 *  5. 输出 SVG 字符串
 */
object Vectorizer {

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int, val color: Int)

    data class Result(
        val rects: List<Rect>,
        val width: Int,
        val height: Int,
        val svg: String
    )

    fun vectorize(
        source: Bitmap,
        maxDimension: Int = 128,
        paletteSize: Int = 12
    ): Result {
        // ---------- 1. 缩放 ----------
        val sw = source.width
        val sh = source.height
        val scale = minOf(
            maxDimension.toFloat() / sw,
            maxDimension.toFloat() / sh,
            1f
        )
        val w = (sw * scale).toInt().coerceAtLeast(1)
        val h = (sh * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        // ---------- 2. 颜色量化 ----------
        val freq = HashMap<Int, Int>()
        for (p in pixels) {
            val key = quantKey(p)
            freq[key] = (freq[key] ?: 0) + 1
        }
        val palette = freq.entries
            .sortedByDescending { it.value }
            .take(paletteSize)
            .map { (it.key shl 0) or 0xFF000000.toInt() and 0xFFFFFFFF.toInt() }
            .map { it or 0xFF000000.toInt() }
            .toMutableList()

        // 若调色板为空（理论上不会），补一个白色
        if (palette.isEmpty()) palette.add(0xFFFFFFFF.toInt())

        // ---------- 3. 像素 → 调色板索引 ----------
        val indices = IntArray(w * h) { i -> nearestIndex(pixels[i], palette) }

        // ---------- 4. 贪心合并为矩形 ----------
        val used = BooleanArray(w * h)
        val rects = ArrayList<Rect>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (used[idx]) continue
                val c = indices[idx]

                // 向右扩展
                var xEnd = x
                while (xEnd + 1 < w &&
                    !used[y * w + xEnd + 1] &&
                    indices[y * w + xEnd + 1] == c
                ) {
                    xEnd++
                }

                // 向下扩展（要求整行都同色）
                var yEnd = y
                outer@ while (yEnd + 1 < h) {
                    for (xx in x..xEnd) {
                        val j = (yEnd + 1) * w + xx
                        if (used[j] || indices[j] != c) break@outer
                    }
                    yEnd++
                }

                // 标记已用
                for (yy in y..yEnd) {
                    val base = yy * w
                    for (xx in x..xEnd) {
                        used[base + xx] = true
                    }
                }

                rects.add(
                    Rect(
                        x = x,
                        y = y,
                        w = xEnd - x + 1,
                        h = yEnd - y + 1,
                        color = palette[c]
                    )
                )
            }
        }

        // ---------- 5. 生成 SVG ----------
        val svg = buildSvg(rects, w, h)
        return Result(rects, w, h, svg)
    }

    /** 按 R/G/B 高位（每通道 3 bit）合并成一个分箱 key */
    private fun quantKey(p: Int): Int {
        val r = (p shr 16) and 0xE0
        val g = (p shr 8) and 0xE0
        val b = p and 0xE0
        return (r shl 16) or (g shl 8) or b
    }

    /** 在调色板中找与 p 最接近的颜色索引（RGB 欧氏距离） */
    private fun nearestIndex(p: Int, palette: List<Int>): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in palette.indices) {
            val pc = palette[i]
            val dr = r - ((pc shr 16) and 0xFF)
            val dg = g - ((pc shr 8) and 0xFF)
            val db = b - (pc and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun buildSvg(rects: List<Rect>, w: Int, h: Int): String {
        val sb = StringBuilder(rects.size * 80 + 256)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
            .append("viewBox=\"0 0 ").append(w).append(' ').append(h).append("\" ")
            .append("width=\"").append(w).append("\" ")
            .append("height=\"").append(h).append("\">\n")
        sb.append("<rect width=\"").append(w)
            .append("\" height=\"").append(h)
            .append("\" fill=\"#FFFFFF\"/>\n")
        for (r in rects) {
            sb.append("<rect x=\"").append(r.x)
                .append("\" y=\"").append(r.y)
                .append("\" width=\"").append(r.w)
                .append("\" height=\"").append(r.h)
                .append("\" fill=\"#").append(hex(r.color))
                .append("\"/>\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun hex(c: Int): String = String.format("%06X", c and 0xFFFFFF)
}
