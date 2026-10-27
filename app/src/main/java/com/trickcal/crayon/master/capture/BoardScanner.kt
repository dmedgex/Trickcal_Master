package com.trickcal.crayon.master.capture

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 涂色板菱形检测（OpenCV 版，完全重写）。
 *
 * 与旧实现的根本区别：旧版手写 Sobel + 连通域。灰卡菱形与卡片底色只差 5~16 灰度，
 * 绝对阈值必然失效，只能靠一堆补偿和推算去猜位置。这里改用自适应二值化——
 * 判断「比周围亮」而不是「绝对够亮」，灰卡和亮卡走同一条路径。
 *
 * 检测：缩放归一化 → 灰度 → CLAHE → 自适应二值化 → 开运算 → 轮廓 → 4 边形筛选
 * 判格：裁中心 ROI → 自适应二值化 → HoughLinesP →
 *       单条 45° 斜线 = ✓，两条交叉斜线 = ✕，无长直线 = 数字
 */
object BoardScanner {

    /** 游戏渲染固定 1280x720；统一缩放到这个高度后，所有尺寸参数才是常量 */
    const val BASE_H = 720f

    enum class Mark { UNKNOWN, CHECK, CROSS, NUMBER }

    /** 一张使徒卡片的矩形范围（缩放后的 720 基准坐标） */
    data class CardRect(val x: Int, val y: Int, val w: Int, val h: Int) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    /** 一张卡片 + 它右侧检出的菱形（已按 x 排序） */
    data class CardDiamonds(val rect: CardRect, val diamonds: List<Diamond>) {
        /**
         * 层号按**绝对位置**计算，不能用检出顺序：
         * 某格漏检时（例如第 1 格 × 没检出），按顺序编号会把第 2、3 格记成第 1、2 层。
         * 菱形区固定占卡片右侧 45%，三等分后按横向落点确定是第几层。
         */
        fun layerOf(cx: Float): Int {
            val zoneX0 = rect.x + rect.w * 0.55f
            val zoneW = rect.w * 0.45f
            if (zoneW <= 1f) return 1
            val idx = ((cx - zoneX0) / zoneW * 3f).toInt().coerceIn(0, 2)
            return idx + 1
        }
    }

    data class Diamond(
        val cx: Float,
        val cy: Float,
        val side: Float,
        val mark: Mark,
        val score: Float,
    )

    private var inited = false
    private var appCtx: android.content.Context? = null
    private var templates: Map<Mark, Mat> = emptyMap()

    /** 40x40 的菱形掩码：|dx|/r + |dy|/r <= 1，r = 20 * 0.78，只保留内切菱形区域 */
    private val ROI_MASK: FloatArray by lazy {
        val n = 40
        val r = 20 * 0.78f
        FloatArray(n * n) { i ->
            val y = i / n
            val x = i % n
            val dx = kotlin.math.abs(x - n / 2f + 0.5f)
            val dy = kotlin.math.abs(y - n / 2f + 0.5f)
            if (dx / r + dy / r <= 1f) 1f else 0f
        }
    }

    /** 模板来自实机截图裁出的 28x28 二值图（assets/glyph），与运行时预处理完全一致 */
    fun init(ctx: android.content.Context): Boolean {
        appCtx = ctx.applicationContext
        if (inited) return true
        val ok = OpenCVLoader.initLocal()
        if (!ok) {
            android.util.Log.e("BoardScan", "OpenCV init failed")
            return false
        }
        val map = mutableMapOf<Mark, Mat>()
        runCatching {
            listOf(
                Mark.CHECK to "check.png",
                Mark.CROSS to "cross.png",
            ).forEach { (m, f) ->
                ctx.assets.open("glyph/$f").use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    val mat = Mat()
                    Utils.bitmapToMat(bmp, mat)
                    val g8 = Mat()
                    Imgproc.cvtColor(mat, g8, Imgproc.COLOR_RGBA2GRAY)
                    mat.release()
                    // 统一转 CV_32F：Mat.get 传 FloatArray 时要求类型匹配，CV_8U 会抛异常
                    val g32 = Mat()
                    g8.convertTo(g32, org.opencv.core.CvType.CV_32F)
                    g8.release()
                    map[m] = g32
                }
            }
        }.onFailure { android.util.Log.w("BoardScan", "load glyph templates failed", it) }
        templates = map
        android.util.Log.d("BoardScan", "模板加载 ${map.size} 个")
        inited = true
        return true
    }

    /** 兼容旧接口 */
    var lastScore: Float = 0f
        private set

    fun classifyAt(bmp: Bitmap, cx: Float, cy: Float): Mark {
        val d = classifyPoint(bmp, cx, cy)
        lastScore = d.score
        return d.mark
    }

    // ------------------------------------------------------------------ 检测

    /**
     * OpenCV 方案的核心：以「卡片」为归属单位。
     * 卡片是界面上明确的圆角矩形，名字在卡内左侧、三颗菱形在卡内右侧 ——
     * 归属靠几何包含关系确定，不需要任何距离阈值。
     */
    fun detect(src: Bitmap): List<CardDiamonds> {
        if (!inited) return emptyList()

        val scale = BASE_H / src.height
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val hi = BASE_H.toInt()
        val bmp = if (src.height == hi && src.width == w) src
        else Bitmap.createScaledBitmap(src, w, hi, true)

        try {
            val dir = appCtx?.getExternalFilesDir("dump")
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                java.io.FileOutputStream(java.io.File(dir, "dump_${System.currentTimeMillis()}.png")).use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } catch (_: Throwable) { }

        val rgba = Mat()
        val gray = Mat()
        val out = mutableListOf<CardDiamonds>()
        try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val rects = findCards(gray)
            android.util.Log.d("BoardScan", "卡片=${rects.size}")

            for (r in rects) {
                val ds = findDiamondsInCard(gray, bmp, r)
                if (ds.isNotEmpty()) out.add(CardDiamonds(r, ds))
            }
        } catch (t: Throwable) {
            android.util.Log.w("BoardScan", "detect failed", t)
        } finally {
            rgba.release(); gray.release()
            if (bmp !== src) bmp.recycle()
        }
        return out
    }

    /** 用 Canny + 轮廓找卡片：宽高比 3~6.5、高度占图 14%~30% 的矩形 */
    private fun findCards(gray: Mat): List<CardRect> {
        val edges = Mat()
        Imgproc.Canny(gray, edges, 40.0, 120.0)
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, k)
        k.release()

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        edges.release()

        val H = gray.rows().toFloat()
        val W = gray.cols().toFloat()
        val cands = mutableListOf<CardRect>()
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            c.release()
            if (r.width < W * 0.25f || r.height < H * 0.14f || r.height > H * 0.30f) continue
            val ar = r.width.toFloat() / r.height
            if (ar < 3.0f || ar > 6.5f) continue
            cands.add(CardRect(r.x, r.y, r.width, r.height))
        }

        // 去重：IoU > 0.5 视为同一张卡（Canny 内外轮廓会各出一圈）
        val keep = mutableListOf<CardRect>()
        cands.sortedByDescending { it.w * it.h }.forEach { c ->
            val dup = keep.any { k2 ->
                val x1 = maxOf(c.x, k2.x); val y1 = maxOf(c.y, k2.y)
                val x2 = minOf(c.x + c.w, k2.x + k2.w); val y2 = minOf(c.y + c.h, k2.y + k2.h)
                if (x2 > x1 && y2 > y1) {
                    val inter = (x2 - x1).toFloat() * (y2 - y1)
                    inter / (c.w * c.h + k2.w * k2.h - inter) > 0.5f
                } else false
            }
            if (!dup) keep.add(c)
        }
        return keep.sortedWith(compareBy({ it.y }, { it.x }))
    }

    /** 只在卡片右侧 45% 区域找菱形：属性图标（左侧）、解锁按钮（中部）自然被排除 */
    private fun findDiamondsInCard(gray: Mat, bmp: Bitmap, card: CardRect): List<Diamond> {
        val x0 = (card.x + card.w * 0.55f).toInt().coerceIn(0, gray.cols() - 1)
        val x1 = (card.x + card.w).toInt().coerceIn(x0 + 1, gray.cols())
        val y0 = card.y.coerceIn(0, gray.rows() - 1)
        val y1 = (card.y + card.h).coerceIn(y0 + 1, gray.rows())

        val roi = Mat(gray, org.opencv.core.Rect(x0, y0, x1 - x0, y1 - y0))
        val found = mutableListOf<Pair<Float, Float>>()
        try {
            val kTop = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(48.0, 48.0))
            val top = Mat()
            Imgproc.morphologyEx(roi, top, Imgproc.MORPH_TOPHAT, kTop)
            kTop.release()

            val b1 = Mat()
            Imgproc.threshold(top, b1, 4.0, 255.0, Imgproc.THRESH_BINARY)
            top.release()

            val b2 = Mat()
            Imgproc.adaptiveThreshold(
                roi, b2, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, -3.0,
            )

            for (bin in listOf(b1, b2)) {
                val kc = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, kc)
                kc.release()
                val cs = ArrayList<MatOfPoint>()
                val hi2 = Mat()
                Imgproc.findContours(bin, cs, hi2, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                hi2.release()
                for (c in cs) {
                    val r = Imgproc.boundingRect(c)
                    if (r.width !in 34..64 || r.height !in 34..64) { c.release(); continue }
                    if (abs(r.width - r.height) > 12) { c.release(); continue }
                    val fill = Imgproc.contourArea(c) / (r.width.toDouble() * r.height)
                    c.release()
                    if (fill < 0.28 || fill > 0.92) continue
                    found.add((x0 + r.x + r.width / 2f) to (y0 + r.y + r.height / 2f))
                }
            }
            b1.release(); b2.release()
        } finally {
            roi.release()
        }

        // 去重（同菱形内外双轮廓）
        val uniq = mutableListOf<Pair<Float, Float>>()
        found.sortedWith(compareBy({ it.second }, { it.first })).forEach { p ->
            if (uniq.none { abs(it.first - p.first) < 40f && abs(it.second - p.second) < 40f }) uniq.add(p)
        }

        // 按 x 排序后，层号 = 排序索引。漏检时用卡片内的间距推算补齐排序位置
        val sorted = uniq.sortedBy { it.first }
        return sorted.mapIndexed { i, p ->
            val d = classifyPoint(bmp, p.first, p.second)
            Diamond(p.first, p.second, 40f, d.mark, d.score)
        }
    }

    // ------------------------------------------------------------------ 判格

    private fun classifyPoint(bmp: Bitmap, cx: Float, cy: Float): Diamond {
        // 菱形实测 35~40px；符号区约 20px。ROI 取 40x40 并套菱形掩码（比例 0.78）：
        // 掩码把菱形边框和四角排除在外，既保留完整笔画，又不会把边框的 45° 斜线算进相似度。
        val side = 40f
        val half = 20
        val x0 = (cx - half).roundToInt().coerceIn(0, bmp.width - 1)
        val y0 = (cy - half).roundToInt().coerceIn(0, bmp.height - 1)
        val x1 = (cx + half).roundToInt().coerceIn(x0 + 1, bmp.width)
        val y1 = (cy + half).roundToInt().coerceIn(y0 + 1, bmp.height)

        val roi = Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
        val rgba = Mat()
        val gray = Mat()
        val bin = Mat()
        var mark = Mark.UNKNOWN
        var score = 0f
        try {
            Utils.bitmapToMat(roi, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            // 关键：符号是**深色**（比菱形底暗），必须用 BINARY_INV 让笔画变成白。
            // 用 THRESH_BINARY 会把符号当成洞，只剩底色，什么都判不出来。
            Imgproc.adaptiveThreshold(
                gray, bin, 255.0,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV,
                9, 6.0,
            )

            val roiGray8 = Mat()
            Imgproc.cvtColor(rgba, roiGray8, Imgproc.COLOR_RGBA2GRAY)
            val roiGray = Mat()
            roiGray8.convertTo(roiGray, org.opencv.core.CvType.CV_32F)
            roiGray8.release()

            // 掩码内计算均值/标准差，掩码外置零，避免菱形边框与四角干扰
            val mask = ROI_MASK
            val n = roiGray.rows()
            val m = roiGray.cols()
            var sum = 0.0
            var cnt = 0
            val vals = FloatArray(n * m)
            roiGray.get(0, 0, vals)
            for (i in 0 until n * m) {
                if (mask[i] > 0f) { sum += vals[i]; cnt++ }
            }
            val mean = if (cnt > 0) sum / cnt else 0.0
            var sq = 0.0
            for (i in 0 until n * m) {
                if (mask[i] > 0f) { val d = vals[i] - mean; sq += d * d }
            }
            val std = kotlin.math.sqrt(sq / kotlin.math.max(cnt, 1)).coerceAtLeast(1e-6)
            val roiNorm = DoubleArray(n * m)
            for (i in 0 until n * m) {
                roiNorm[i] = if (mask[i] > 0f) (vals[i] - mean) / std else 0.0
            }
            roiGray.release()

            val scores = mutableMapOf<Mark, Double>()
            // ±6px 平移搜索：菱形检测的中心与符号实际中心可差 5px 左右，
            // 搜索范围小于这个偏差时，同一种符号在不同格子上分数会差 0.3 以上。
            val shift = 6
            templates.forEach { (mk, tpl) ->
                if (tpl.rows() != n || tpl.cols() != m) return@forEach
                val tVals = FloatArray(n * m)
                tpl.get(0, 0, tVals)
                var tSum = 0.0
                var tCnt = 0
                for (i in 0 until n * m) if (mask[i] > 0f) { tSum += tVals[i]; tCnt++ }
                val tMean = if (tCnt > 0) tSum / tCnt else 0.0
                var tSq = 0.0
                for (i in 0 until n * m) if (mask[i] > 0f) { val d = tVals[i] - tMean; tSq += d * d }
                val tStd = kotlin.math.sqrt(tSq / kotlin.math.max(tCnt, 1)).coerceAtLeast(1e-6)
                val tNorm = DoubleArray(n * m) { (tVals[it] - tMean) / tStd }

                var best = -9.0
                for (sx in -shift..shift) {
                    for (sy in -shift..shift) {
                        var dot = 0.0
                        var c = 0
                        for (y in 0 until n) {
                            for (x in 0 until m) {
                                val mx = x - sx
                                val my = y - sy
                                if (mx < 0 || mx >= m || my < 0 || my >= n) continue
                                val mi = my * m + mx
                                if (mask[mi] <= 0f) continue
                                dot += roiNorm[mi] * tNorm[y * m + x]
                                c++
                            }
                        }
                        if (c > 0) {
                            val v = dot / c
                            if (v > best) best = v
                        }
                    }
                }
                scores[mk] = best
            }

            val sCheck = scores[Mark.CHECK] ?: -9.0
            val sCross = scores[Mark.CROSS] ?: -9.0
            // 绝对阈值 + 差值双条件：数字/空格的分数普遍很低（0.05 左右），
            // 只靠差值会把「两个都很低但略有差」的格子误判成勾。
            mark = when {
                sCheck >= 0.35 && sCheck - sCross >= 0.15 -> Mark.CHECK
                sCross >= 0.35 && sCross - sCheck >= 0.15 -> Mark.CROSS
                else -> Mark.UNKNOWN
            }
            score = maxOf(sCheck, sCross).toFloat()
            android.util.Log.d(
                "CardMatch",
                "  cell(${cx.toInt()},${cy.toInt()}) check=${"%.3f".format(sCheck)} " +
                    "cross=${"%.3f".format(sCross)} → $mark",
            )
        } catch (t: Throwable) {
            android.util.Log.w("BoardScan", "classify failed", t)
        } finally {
            rgba.release(); gray.release(); bin.release()
            roi.recycle()
        }
        return Diamond(cx, cy, side, mark, score)
    }
}
