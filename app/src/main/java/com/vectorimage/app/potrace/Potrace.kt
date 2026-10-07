package com.vectorimage.app.potrace

import android.graphics.Bitmap

object Potrace {

    fun trace(bitmap: Bitmap, params: PotraceParams = PotraceParams()): List<PotracePath> {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h) { i ->
            val p = px[i]
            ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        val threshold = otsu(gray)
        val binary = BooleanArray(w * h) { gray[it] < threshold }

        val paths = PathDecomposer.decompose(binary, w, h, params)

        for (path in paths) {
            PolygonFitter.fit(path)
            CurveGenerator.generate(path, params)
            if (params.optimizeCurve) {
                CurveOptimizer.optimize(path, params)
            }
        }

        return paths
    }

    private fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.coerceIn(0, 255)]++
        val total = gray.size
        var sumAll = 0.0
        for (i in 0..255) sumAll += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var thr = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > best) { best = v; thr = i }
        }
        return thr
    }
}
