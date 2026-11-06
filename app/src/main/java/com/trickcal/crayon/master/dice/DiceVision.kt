package com.trickcal.crayon.master.dice

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 对局场景。回合归属由 [FrameInfo.isMyTurn] 单独表示，与脚本的 match_orange / match_green 对应。 */
enum class DiceScene { MATCH, MAIN, RESULT, VICTORY, DIALOG, UNKNOWN }

/** 计分板某一格的状态。overlay 表示被提示面板遮挡，本帧不参与判定。 */
enum class CellStatus { FILLED, EMPTY, OVERLAY, GREEN }

/**
 * 骰子外观。脚本用颜色分割定位骰子：绿色是万能骰，橙色是普通骰。
 * 不要因为某张调试图出现别的颜色就扩展这个枚举 —— 真实对局只有这两种。
 */
enum class DieKind { ORANGE, GREEN }

data class DetectedDie(
    val kind: DieKind,
    /** null 表示绿骰万能（掷出 1 点或点数识别不出） */
    val value: Int?,
    val x: Float,
    val y: Float,
) {
    val isNormal: Boolean get() = kind != DieKind.GREEN
    val wildcard: Boolean get() = kind == DieKind.GREEN && value == null
    fun toDie(): Die = Die(isNormal, value)
}

/** 一帧的完整读数。occupancy 与 round 只在 MATCH 时有意义。 */
data class FrameInfo(
    val scene: DiceScene,
    /** 是否我方回合。脚本用顶部背景色判断：绿底=对方，橙底=我方。 */
    val isMyTurn: Boolean = false,
    val table: List<DetectedDie> = emptyList(),
    val slots: List<DetectedDie> = emptyList(),
    val occupancy: Map<DiceCategory, CellStatus> = emptyMap(),
    /** 左上角 N/8 的分子；已登记 k 格时显示 k+1 */
    val round: Int? = null,
    val roundTotal: Int? = null,
    val confidence: DiceGeometry.Confidence = DiceGeometry.Confidence.IDENTITY,
) {
    val diceCount: Int get() = table.size + slots.size
    val allDice: List<DetectedDie> get() = slots + table
}

/**
 * 视觉层：把 vision.py 的识别逻辑逐函数转写到 OpenCV Android。
 *
 * 输入必须是**已归一化到 720 高**的画面（[DiceGeometry.normalize]），
 * 坐标则用 [DiceGeometry.Mapping] 从 1280×720 基准换算。这两件事分开做，
 * 是因为阈值只跟高度有关（缩放后不变），而 x 位置随设备变化。
 *
 * 与脚本的一处刻意差异：场景判定不再只看背景色。实测存在蓝色主题的对局帧，
 * 其计分板是浅蓝色，米色判据会把它判成 unknown。改成「颜色采样 → 左上角 N/8 兜底」，
 * N/8 是结构特征，跨配色稳定。
 */
object DiceVision {

    // ---- 场景采样点（1280×720 基准）----
    private const val P_SCOREBOARD_X = 60
    private const val P_SCOREBOARD_Y = 300
    private const val P_BG_TOP_X = 640
    private const val P_BG_TOP_Y = 25
    private const val P_CENTER_X = 640
    private const val P_CENTER_Y = 360
    private const val P_TAN_X = 400
    private const val P_TAN_Y = 300
    private const val REGION_R = 4

    // ---- 骰子 ----
    private const val DIE_AREA_MIN = 1200.0
    private const val DIE_AREA_MAX = 14000.0
    private const val DIE_NOMINAL_AREA = 2900.0
    private const val DIE_SIDE_MIN = 36.0
    private const val DIE_SIDE_MAX = 100.0
    private const val DIE_ASPECT_MIN = 0.60
    private const val DIE_ASPECT_MAX = 1.70
    private const val DIE_FILL_MIN = 0.55
    private const val DIE_SIZE = 72
    private const val SPLIT_MIN_PIXELS = 600

    /** 普通骰。V>195 是与深棕桌面分开的关键（桌面同色系但更暗） */
    private val ORANGE_LO = Scalar(0.0, 140.0, 195.0)
    private val ORANGE_HI = Scalar(25.0, 255.0, 255.0)
    /** 万能骰 */
    private val GREEN_LO = Scalar(38.0, 120.0, 130.0)
    private val GREEN_HI = Scalar(95.0, 255.0, 255.0)
    private val WHITE_LO = Scalar(0.0, 0.0, 215.0)
    private val WHITE_HI = Scalar(180.0, 75.0, 255.0)

    // ---- 槽位 ----
    private const val SLOT_DIE_MIN_PIXELS = 900.0
    private const val SLOT_OFFSET_MAX = 16f

    // ---- 计分板 ----
    private const val INK_THRESHOLD = 90
    private const val INK_MAX = 700
    private val OCC_GREEN_LO = Scalar(35.0, 80.0, 80.0)
    private val OCC_GREEN_HI = Scalar(95.0, 255.0, 255.0)
    private const val OCC_GREEN_MIN = 150

    // ---- 计数器 ----
    private const val CNT_GLYPH_W = 24
    private const val CNT_GLYPH_H = 32
    private const val CNT_COMP_MIN_AREA = 40
    private const val CNT_COMP_MIN_H = 12
    private const val CNT_SLASH_DX_MIN = 3.0
    private const val CNT_MATCH_MIN = 0.72
    private const val CNT_DENOM_EXPECT = 8
    private val CNT_LO = Scalar(30.0, 40.0, 20.0)
    private val CNT_HI = Scalar(95.0, 255.0, 140.0)
    private val CNT_LO2 = Scalar(25.0, 30.0, 10.0)
    private val CNT_HI2 = Scalar(95.0, 255.0, 205.0)

    // ---- 弹窗 ----
    private const val DLG_GREEN_MIN = 8000
    private const val DLG_WHITE_MIN = 60000

    private class DigitTemplates(val glyphs: Array<ByteArray>, val labels: IntArray) {
        val w = CNT_GLYPH_W
        val h = CNT_GLYPH_H
    }

    @Volatile
    private var digits: DigitTemplates? = null

    @Volatile
    private var loaded = false

    /** 读 assets/dice/digits.bin。失败不致命：N/8 读不到就退回颜色分类。 */
    fun init(ctx: Context): Boolean {
        if (loaded) return digits != null
        loaded = true
        if (!DiceOpenCv.ensure()) return false
        digits = runCatching {
            ctx.assets.open("dice/digits.bin").use { input ->
                val bytes = input.readBytes()
                val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val magic = ByteArray(8)
                buf.get(magic)
                require(String(magic, Charsets.US_ASCII) == "DICEDIG1")
                val count = buf.int
                val gw = buf.int
                val gh = buf.int
                require(gw == CNT_GLYPH_W && gh == CNT_GLYPH_H)
                val glyphs = Array(count) { ByteArray(gw * gh) }
                for (g in glyphs) buf.get(g)
                val labels = IntArray(count) { buf.int }
                DigitTemplates(glyphs, labels)
            }
        }.getOrNull()
        return digits != null
    }

    /**
     * 分析一帧。[normalized] 必须是 720 高的画面，[map] 来自 [DiceGeometry.solve]。
     */
    fun analyze(normalized: Bitmap, map: DiceGeometry.Mapping): FrameInfo {
        if (!DiceOpenCv.ensure()) return FrameInfo(DiceScene.UNKNOWN)
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(normalized, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)

            // 与脚本主循环一致：每帧先查退出确认弹窗。它会盖住采样点导致场景误判，
            // 且一旦误点『结束』整局就没了，所以判定要排在场景分类之前。
            if (detectExitDialog(hsv, map)) return FrameInfo(DiceScene.DIALOG)

            val scene = classify(rgb, map)
            if (scene.scene != DiceScene.MATCH) return FrameInfo(scene.scene)

            val table = readTable(rgb, hsv, map)
            val slots = readSlots(rgb, hsv, map)
            val occ = readOccupancy(hsv, gray, map)
            val counter = readRoundCounter(hsv, map)
            return FrameInfo(
                scene = scene.scene,
                isMyTurn = scene.myTurn,
                table = table,
                slots = slots,
                occupancy = occ,
                round = counter?.first,
                roundTotal = counter?.second,
            )
        } catch (_: Throwable) {
            return FrameInfo(DiceScene.UNKNOWN)
        } finally {
            rgba.release(); rgb.release(); hsv.release(); gray.release()
        }
    }

    // ------------------------------------------------------------------ 场景

    private data class SceneResult(val scene: DiceScene, val myTurn: Boolean)

    /**
     * 场景分类，判据与脚本 vision.classify_scene 一致：
     * 先看计分板是否米色纸，再用顶部背景的 R 通道区分回合（绿底 R≈0 是对方，橙底 R≈184 是我方）。
     * 通道顺序别搞反：Mat 是 RGB，脚本是 BGR。
     */
    private fun classify(rgb: Mat, map: DiceGeometry.Mapping): SceneResult {
        val paper = meanRgb(rgb, map, P_SCOREBOARD_X, P_SCOREBOARD_Y)
        val center = meanRgb(rgb, map, P_CENTER_X, P_CENTER_Y)
        val tan = meanRgb(rgb, map, P_TAN_X, P_TAN_Y)

        // 对局：计分板米色纸（B 165~215 / G 205~245 / R 235~255）
        if (paper[0] in 235.0..255.0 && paper[1] in 205.0..245.0 && paper[2] in 165.0..215.0) {
            val bg = meanRgb(rgb, map, P_BG_TOP_X, P_BG_TOP_Y)
            val r = bg[0]
            return when {
                r < 60 -> SceneResult(DiceScene.MATCH, false)   // 对方回合，等待
                r > 120 -> SceneResult(DiceScene.MATCH, true)   // 我方回合
                else -> SceneResult(DiceScene.UNKNOWN, false)
            }
        }
        // 结算：中心橙纸（B 120~185 / G 180~225 / R 215~255）
        if (center[0] in 215.0..255.0 && center[1] in 180.0..225.0 && center[2] in 120.0..185.0) {
            return SceneResult(DiceScene.RESULT, false)
        }
        // 胜利：中心白色骰面 + 四周深色毡
        if (center[0] >= 235 && center[1] >= 205 && center[2] >= 200 &&
            tan[0] < 120 && tan[2] < 120
        ) {
            return SceneResult(DiceScene.VICTORY, false)
        }
        // 主界面：暖棕（B 40~120 / G 130~200 / R 200~255）
        if (tan[0] in 200.0..255.0 && tan[1] in 130.0..200.0 && tan[2] in 40.0..120.0) {
            return SceneResult(DiceScene.MAIN, false)
        }
        return SceneResult(DiceScene.UNKNOWN, false)
    }

    /** 取 (x,y) 周围 (2r+1)² 的均值，返回 RGB 三通道。用映射后的坐标，避免采样点随分辨率漂移。 */
    private fun meanRgb(rgb: Mat, map: DiceGeometry.Mapping, bx: Int, by: Int): DoubleArray {
        val cx = map.x(bx).roundToInt()
        val cy = map.y(by).roundToInt()
        val x0 = max(0, cx - REGION_R)
        val x1 = min(rgb.cols(), cx + REGION_R + 1)
        val y0 = max(0, cy - REGION_R)
        val y1 = min(rgb.rows(), cy + REGION_R + 1)
        if (x1 <= x0 || y1 <= y0) return doubleArrayOf(0.0, 0.0, 0.0)
        val sub = rgb.submat(y0, y1, x0, x1)
        val m = Core.mean(sub)
        sub.release()
        return doubleArrayOf(m.`val`[0], m.`val`[1], m.`val`[2])
    }

    // ------------------------------------------------------------------ 骰子

    private fun readTable(rgb: Mat, hsv: Mat, map: DiceGeometry.Mapping): List<DetectedDie> {
        val x0 = map.x(DiceGeometry.TABLE_X0).roundToInt().coerceIn(0, hsv.cols() - 1)
        val x1 = map.x(DiceGeometry.TABLE_X1).roundToInt().coerceIn(x0 + 1, hsv.cols())
        val y0 = map.y(DiceGeometry.TABLE_Y0).roundToInt().coerceIn(0, hsv.rows() - 1)
        val y1 = map.y(DiceGeometry.TABLE_Y1).roundToInt().coerceIn(y0 + 1, hsv.rows())

        val roiHsv = Mat(hsv, Rect(x0, y0, x1 - x0, y1 - y0))
        val roiRgb = Mat(rgb, Rect(x0, y0, x1 - x0, y1 - y0))
        val out = ArrayList<DetectedDie>(5)
        try {
            for (kind in DieKind.entries) {
                val lo = if (kind == DieKind.ORANGE) ORANGE_LO else GREEN_LO
                val hi = if (kind == DieKind.ORANGE) ORANGE_HI else GREEN_HI
                val mask = Mat()
                Core.inRange(roiHsv, lo, hi, mask)
                val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k)
                k.release()

                for (cand in splitAndFind(mask)) {
                    val die = normalizeDie(roiRgb, cand.rect, cand.area) ?: continue
                    val pips = readPips(die)
                    die.release()
                    // 绿骰掷 1 点或点数读不出，一律按万能处理（脚本的既有规则）
                    val wildcard = kind == DieKind.GREEN && (pips == null || pips == 1)
                    val value = when {
                        wildcard -> null
                        pips == null && kind != DieKind.GREEN -> 1
                        else -> pips
                    }
                    out.add(
                        DetectedDie(
                            kind = kind,
                            value = value,
                            x = cand.rect.center.x.toFloat() + x0,
                            y = cand.rect.center.y.toFloat() + y0,
                        ),
                    )
                }
                mask.release()
            }
        } finally {
            roiHsv.release(); roiRgb.release()
        }
        return out
    }

    private class Candidate(val rect: RotatedRect, val area: Double)

    /**
     * 连通域 → 候选骰面。粘连的大块按「面积 / 单颗标称面积」估个数，
     * 再用 k-means 把像素坐标拆开（脚本用同一套做法，实测能拆开贴在一起的两颗）。
     * 形状校验淘汰同色系干扰：万能提示文字长宽比约 2.0、填充率仅 0.2。
     */
    private fun splitAndFind(mask: Mat): List<Candidate> {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val n = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids)
        val st = IntArray(n * 5)
        stats.get(0, 0, st)
        val out = ArrayList<Candidate>(8)

        for (j in 1 until n) {
            val a = st[j * 5 + 4].toDouble()
            if (a < DIE_AREA_MIN || a > DIE_AREA_MAX) continue
            val bw = st[j * 5 + 2]
            val bh = st[j * 5 + 3]
            val bl = st[j * 5]
            val bt = st[j * 5 + 1]
            val k = max(1, (a / DIE_NOMINAL_AREA).roundToInt())

            val px = ArrayList<Float>(a.toInt().coerceAtLeast(16) * 2)
            val lab = IntArray(bw * bh)
            labels.submat(bt, bt + bh, bl, bl + bw).get(0, 0, lab)
            for (yy in 0 until bh) {
                for (xx in 0 until bw) {
                    if (lab[yy * bw + xx] == j) {
                        px.add((bl + xx).toFloat())
                        px.add((bt + yy).toFloat())
                    }
                }
            }
            val count = px.size / 2
            if (count < 8) continue

            if (k > 1) {
                val data = Mat(count, 2, CvType.CV_32F)
                data.put(0, 0, px.toFloatArray())
                val best = Mat()
                val centers = Mat()
                val crit = TermCriteria(
                    TermCriteria.EPS + TermCriteria.MAX_ITER, 20, 1.0,
                )
                runCatching {
                    Core.kmeans(data, k, best, crit, 5, Core.KMEANS_PP_CENTERS, centers)
                }
                val assign = IntArray(count)
                if (best.total() > 0) best.get(0, 0, assign)
                for (q in 0 until k) {
                    val sub = ArrayList<Float>(256)
                    for (i in 0 until count) {
                        if (assign.getOrElse(i) { 0 } == q) {
                            sub.add(px[i * 2])
                            sub.add(px[i * 2 + 1])
                        }
                    }
                    if (sub.size / 2 > SPLIT_MIN_PIXELS) {
                        addIfShaped(sub, sub.size / 2.0, out)
                    }
                }
                data.release(); best.release(); centers.release()
            } else {
                addIfShaped(px, a, out)
            }
        }
        labels.release(); stats.release(); centroids.release()
        return out
    }

    private fun addIfShaped(px: ArrayList<Float>, area: Double, out: MutableList<Candidate>) {
        if (px.size < 6) return
        val pts = MatOfPoint2f()
        pts.fromList((0 until px.size / 2).map { Point(px[it * 2].toDouble(), px[it * 2 + 1].toDouble()) })
        val rect = runCatching { Imgproc.minAreaRect(pts) }.getOrNull()
        pts.release()
        if (rect == null) return
        if (shapeOk(rect, area)) out.add(Candidate(rect, area))
    }

    private fun shapeOk(rect: RotatedRect, area: Double): Boolean {
        val w = max(rect.size.width, 1.0)
        val h = max(rect.size.height, 1.0)
        if (w < DIE_SIDE_MIN || w > DIE_SIDE_MAX) return false
        if (h < DIE_SIDE_MIN || h > DIE_SIDE_MAX) return false
        val ratio = w / h
        if (ratio < DIE_ASPECT_MIN || ratio > DIE_ASPECT_MAX) return false
        return area / (w * h) >= DIE_FILL_MIN
    }

    /** 以质心裁正方形、按 minAreaRect 的角度转正，再归一化到 72×72 */
    private fun normalizeDie(roi: Mat, rect: RotatedRect, area: Double): Mat? {
        val side = sqrt(max(area, 1.0)).times(1.08).coerceIn(44.0, 90.0)
        val m = Imgproc.getRotationMatrix2D(rect.center, rect.angle.toDouble(), 1.0)
        val big = Mat()
        Imgproc.warpAffine(roi, big, m, Size(roi.cols().toDouble(), roi.rows().toDouble()))
        m.release()
        val half = (side / 2).toInt()
        val x0 = max(0, rect.center.x.toInt() - half)
        val y0 = max(0, rect.center.y.toInt() - half)
        val x1 = min(big.cols(), x0 + 2 * half)
        val y1 = min(big.rows(), y0 + 2 * half)
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            big.release()
            return null
        }
        val crop = Mat(big, Rect(x0, y0, x1 - x0, y1 - y0))
        val out = Mat()
        Imgproc.resize(crop, out, Size(DIE_SIZE.toDouble(), DIE_SIZE.toDouble()))
        crop.release(); big.release()
        return out
    }

    /** 白点计数：圆度 + 尺寸双过滤，超过 6 个视为干扰 */
    private fun readPips(die: Mat): Int? {
        val hsv = Mat()
        val white = Mat()
        try {
            Imgproc.cvtColor(die, hsv, Imgproc.COLOR_RGB2HSV)
            Core.inRange(hsv, WHITE_LO, WHITE_HI, white)
            val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
            Imgproc.morphologyEx(white, white, Imgproc.MORPH_OPEN, k)
            k.release()

            val contours = ArrayList<MatOfPoint>()
            val hier = Mat()
            Imgproc.findContours(white, contours, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hier.release()
            var n = 0
            for (c in contours) {
                val a = Imgproc.contourArea(c)
                if (a < 8.0 || a > 400.0) {
                    c.release()
                    continue
                }
                val c2 = MatOfPoint2f(*c.toArray())
                c.release()
                val center = Point()
                val radius = FloatArray(1)
                Imgproc.minEnclosingCircle(c2, center, radius)
                val peri = Imgproc.arcLength(c2, true)
                c2.release()
                if (peri > 0 && 4 * Math.PI * a / (peri * peri) < 0.55) continue
                if (radius[0] < 2.5 || radius[0] > 14) continue
                n++
            }
            return if (n in 1..6) n else null
        } catch (_: Throwable) {
            return null
        } finally {
            hsv.release(); white.release()
        }
    }

    // ------------------------------------------------------------------ 保留槽

    /**
     * 逐槽开窗读取。槽内骰子会和槽框高亮带粘成一条 140px 宽的带子，
     * 用连通域会把整条带当成一颗；槽位坐标固定（经映射），窗口内直接数白点更稳。
     */
    private fun readSlots(rgb: Mat, hsv: Mat, map: DiceGeometry.Mapping): List<DetectedDie> {
        val out = ArrayList<DetectedDie>(5)
        val rx = (DiceGeometry.SLOT_WIN_R * map.sx).roundToInt().coerceAtLeast(8)
        val ry = (DiceGeometry.SLOT_WIN_R * map.sy).roundToInt().coerceAtLeast(8)
        val cy = map.y(DiceGeometry.SLOT_Y).roundToInt()

        for (bx in DiceGeometry.SLOT_XS) {
            val cx = map.x(bx).roundToInt()
            val x0 = (cx - rx).coerceIn(0, hsv.cols() - 1)
            val x1 = (cx + rx).coerceIn(x0 + 1, hsv.cols())
            val y0 = (cy - ry).coerceIn(0, hsv.rows() - 1)
            val y1 = (cy + ry).coerceIn(y0 + 1, hsv.rows())

            val winHsv = Mat(hsv, Rect(x0, y0, x1 - x0, y1 - y0))
            val winRgb = Mat(rgb, Rect(x0, y0, x1 - x0, y1 - y0))
            val masks = Array(2) { Mat() }
            try {
                val lows = arrayOf(ORANGE_LO, GREEN_LO)
                val highs = arrayOf(ORANGE_HI, GREEN_HI)
                val counts = IntArray(2)
                for (i in 0..1) {
                    Core.inRange(winHsv, lows[i], highs[i], masks[i])
                    counts[i] = Core.countNonZero(masks[i])
                }
                val best = counts.indices.maxByOrNull { counts[it] } ?: 0
                val need = SLOT_DIE_MIN_PIXELS * map.sx * map.sy
                if (counts[best] < need) continue
                val kind = DieKind.entries[best]
                val mask = masks[best]
                // 质心必须贴近槽心，排除恰好飞过槽区的空中骰子
                val mom = Imgproc.moments(mask, true)
                val mx = (mom.m10 / mom.m00).toFloat()
                val my = (mom.m01 / mom.m00).toFloat()
                val halfW = (x1 - x0) / 2f
                val halfH = (y1 - y0) / 2f
                if (abs(mx - halfW) > SLOT_OFFSET_MAX * map.sx ||
                    abs(my - halfH) > SLOT_OFFSET_MAX * map.sy
                ) {
                    continue
                }
                val pips = readPips(winRgb)
                val wildcard = kind == DieKind.GREEN && (pips == null || pips == 1)
                val value = when {
                    wildcard -> null
                    pips == null && kind != DieKind.GREEN -> 1
                    else -> pips
                }
                out.add(DetectedDie(kind, value, cx.toFloat(), cy.toFloat()))
            } finally {
                masks.forEach { it.release() }
                winHsv.release(); winRgb.release()
            }
        }
        return out
    }

    // ------------------------------------------------------------------ 计分板

    /**
     * 逐格判断我方是否已登记。
     * 深色数字 = 已登记；横杠或浅色预览分 = 未登记；绿底数字 = 忽略；
     * 墨迹超过 700 = 提示面板遮挡，本帧不判定（这是「计数虚高」的主要来源）。
     */
    private fun readOccupancy(
        hsv: Mat,
        gray: Mat,
        map: DiceGeometry.Mapping,
    ): Map<DiceCategory, CellStatus> {
        val out = HashMap<DiceCategory, CellStatus>(8)
        val x0 = map.x(DiceGeometry.SCORE_MY_X0).roundToInt().coerceIn(0, hsv.cols() - 1)
        val x1 = map.x(DiceGeometry.SCORE_MY_X1).roundToInt().coerceIn(x0 + 1, hsv.cols())
        val dy = (DiceGeometry.SCORE_ROW_DY * map.sy).roundToInt().coerceAtLeast(2)

        for (cat in DiceCategory.entries) {
            val cy = map.y(DiceGeometry.SCORE_ROWS[cat.solver]).roundToInt()
            val y0 = (cy - dy).coerceIn(0, hsv.rows() - 1)
            val y1 = (cy + dy).coerceIn(y0 + 1, hsv.rows())
            val cellHsv = Mat(hsv, Rect(x0, y0, x1 - x0, y1 - y0))
            val cellGray = Mat(gray, Rect(x0, y0, x1 - x0, y1 - y0))
            try {
                val green = Mat()
                Core.inRange(cellHsv, OCC_GREEN_LO, OCC_GREEN_HI, green)
                val nGreen = Core.countNonZero(green)
                if (nGreen >= OCC_GREEN_MIN) {
                    out[cat] = CellStatus.GREEN
                    green.release()
                    continue
                }
                // 绿底像素不参与墨迹统计（绿底本身偏暗，会把自己算成墨迹）
                val work = cellGray.clone()
                work.setTo(Scalar(255.0), green)
                green.release()

                val ink = countInk(work)
                out[cat] = when {
                    ink > INK_MAX -> CellStatus.OVERLAY
                    ink > INK_THRESHOLD -> CellStatus.FILLED
                    else -> CellStatus.EMPTY
                }
                work.release()
            } finally {
                cellHsv.release(); cellGray.release()
            }
        }
        return out
    }

    /** 以中位数为纸面亮度，低于 paper-45 的像素记为墨迹；中位数用 256 桶直方图求 */
    private fun countInk(gray: Mat): Int {
        val n = gray.rows() * gray.cols()
        if (n <= 0) return 0
        val buf = ByteArray(n)
        gray.get(0, 0, buf)
        val hist = IntArray(256)
        for (b in buf) hist[b.toInt() and 0xFF]++
        var acc = 0
        var median = 127
        val half = n / 2
        for (v in 0 until 256) {
            acc += hist[v]
            if (acc >= half) {
                median = v
                break
            }
        }
        val thr = median - 45
        var ink = 0
        for (b in buf) if ((b.toInt() and 0xFF) < thr) ink++
        return ink
    }

    // ------------------------------------------------------------------ N/8 计数器

    /** 返回 (分子, 分母)；读不到或结构不符返回 null */
    private fun readRoundCounter(hsv: Mat, map: DiceGeometry.Mapping): Pair<Int, Int>? {
        val tpl = digits ?: return null
        for (range in listOf(CNT_LO to CNT_HI, CNT_LO2 to CNT_HI2)) {
            val glyphs = counterGlyphs(hsv, map, range.first, range.second)
            if (glyphs.size < 3) continue
            var valid: Pair<Int, Int>? = null
            var ambiguous = false
            for (i in 1 until glyphs.size - 1) {
                if (!isSlash(glyphs[i].bytes)) continue
                val num = classifyGlyph(tpl, glyphs[i - 1].bytes) ?: continue
                val den = classifyGlyph(tpl, glyphs[i + 1].bytes) ?: continue
                if (den != CNT_DENOM_EXPECT) continue
                if (valid != null && valid != (num to den)) {
                    ambiguous = true
                    break
                }
                valid = num to den
            }
            if (!ambiguous && valid != null) return valid
        }
        return null
    }

    private class Glyph(val bytes: ByteArray, val x: Int, val w: Int, val h: Int)

    private fun counterGlyphs(
        hsv: Mat,
        map: DiceGeometry.Mapping,
        lo: Scalar,
        hi: Scalar,
    ): List<Glyph> {
        val x0 = map.x(DiceGeometry.COUNTER_X0).roundToInt().coerceIn(0, hsv.cols() - 1)
        val x1 = map.x(DiceGeometry.COUNTER_X1).roundToInt().coerceIn(x0 + 1, hsv.cols())
        val y0 = map.y(DiceGeometry.COUNTER_Y0).roundToInt().coerceIn(0, hsv.rows() - 1)
        val y1 = map.y(DiceGeometry.COUNTER_Y1).roundToInt().coerceIn(y0 + 1, hsv.rows())
        val roi = Mat(hsv, Rect(x0, y0, x1 - x0, y1 - y0))
        val mask = Mat()
        try {
            Core.inRange(roi, lo, hi, mask)
            val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k)
            k.release()

            val labels = Mat()
            val stats = Mat()
            val centroids = Mat()
            val n = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids)
            val st = IntArray(n * 5)
            stats.get(0, 0, st)
            val comps = ArrayList<IntArray>(8)
            for (j in 1 until n) {
                val a = st[j * 5 + 4]
                val h = st[j * 5 + 3]
                if (a < CNT_COMP_MIN_AREA || h < CNT_COMP_MIN_H) continue
                comps.add(intArrayOf(st[j * 5], st[j * 5 + 1], st[j * 5 + 2], h, j))
            }
            if (comps.isEmpty()) {
                labels.release(); stats.release(); centroids.release()
                return emptyList()
            }
            val maxH = comps.maxOf { it[3] }
            val kept = comps.filter { it[3] >= 0.6 * maxH }.sortedBy { it[0] }
            val out = ArrayList<Glyph>(kept.size)
            for (c in kept) {
                val gx = c[0]
                val gy = c[1]
                val gw = c[2]
                val gh = c[3]
                val idx = c[4]
                // 批量取整块标签再判断，逐像素 Mat.get 每次都是 JNI 调用，几十个字形会明显拖慢
                val subLabels = labels.submat(gy, gy + gh, gx, gx + gw)
                val lbuf = IntArray(gw * gh)
                subLabels.get(0, 0, lbuf)
                subLabels.release()
                val bin = Mat(gh, gw, CvType.CV_8UC1, Scalar(0.0))
                for (yy in 0 until gh) {
                    for (xx in 0 until gw) {
                        if (lbuf[yy * gw + xx] == idx) bin.put(yy, xx, 255.0)
                    }
                }
                val small = Mat()
                Imgproc.resize(bin, small, Size(CNT_GLYPH_W.toDouble(), CNT_GLYPH_H.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                val bytes = ByteArray(CNT_GLYPH_W * CNT_GLYPH_H)
                small.get(0, 0, bytes)
                bin.release(); small.release()
                out.add(Glyph(bytes, gx + x0, gw, gh))
            }
            labels.release(); stats.release(); centroids.release()
            return out
        } catch (_: Throwable) {
            return emptyList()
        } finally {
            roi.release(); mask.release()
        }
    }

    /**
     * 斜杠判据：上下三分之一的质心水平错位。
     * 不能只看长宽比 —— 本作字体的「1」也是细长条。
     */
    private fun isSlash(g: ByteArray): Boolean {
        var topX = 0.0
        var topN = 0
        var botX = 0.0
        var botN = 0
        for (y in 0 until CNT_GLYPH_H) {
            for (x in 0 until CNT_GLYPH_W) {
                if (g[y * CNT_GLYPH_W + x].toInt() == 0) continue
                if (y < CNT_GLYPH_H / 3) {
                    topX += x; topN++
                } else if (y > 2 * CNT_GLYPH_H / 3) {
                    botX += x; botN++
                }
            }
        }
        if (topN == 0 || botN == 0) return false
        return abs(botX / botN - topX / topN) >= CNT_SLASH_DX_MIN
    }

    private fun classifyGlyph(tpl: DigitTemplates, g: ByteArray): Int? {
        var best = 0.0
        var bestLabel: Int? = null
        for (i in tpl.glyphs.indices) {
            val c = normCorr(g, tpl.glyphs[i])
            if (c > best) {
                best = c
                bestLabel = tpl.labels[i]
            }
        }
        return if (best >= CNT_MATCH_MIN) bestLabel else null
    }

    private fun normCorr(a: ByteArray, b: ByteArray): Double {
        val n = min(a.size, b.size)
        var ma = 0.0
        var mb = 0.0
        for (i in 0 until n) {
            ma += (a[i].toInt() and 0xFF).toDouble()
            mb += (b[i].toInt() and 0xFF).toDouble()
        }
        ma /= n
        mb /= n
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in 0 until n) {
            val va = (a[i].toInt() and 0xFF) - ma
            val vb = (b[i].toInt() and 0xFF) - mb
            num += va * vb
            da += va * va
            db += vb * vb
        }
        val d = sqrt(da * db)
        return if (d < 1e-6) 0.0 else num / d
    }

    // ------------------------------------------------------------------ 弹窗

    /**
     * 「要结束游戏吗?」确认弹窗。
     * 必须同时满足绿色标题栏与大面积纯白主体 —— 只用绿标题栏会误报，
     * 结算画面的大绿叶也满足（实测 4720 vs 真弹窗 18553）。
     */
    private fun detectExitDialog(hsv: Mat, map: DiceGeometry.Mapping): Boolean {
        val scale = map.sx * map.sy
        val green = regionCount(
            hsv, map,
            DiceGeometry.DLG_GREEN_X0, DiceGeometry.DLG_GREEN_Y0,
            DiceGeometry.DLG_GREEN_X1, DiceGeometry.DLG_GREEN_Y1,
            Scalar(40.0, 120.0, 120.0), Scalar(85.0, 255.0, 255.0),
        )
        if (green < DLG_GREEN_MIN * scale) return false
        val white = regionCount(
            hsv, map,
            DiceGeometry.DLG_WHITE_X0, DiceGeometry.DLG_WHITE_Y0,
            DiceGeometry.DLG_WHITE_X1, DiceGeometry.DLG_WHITE_Y1,
            Scalar(0.0, 0.0, 215.0), Scalar(180.0, 45.0, 255.0),
        )
        return white >= DLG_WHITE_MIN * scale
    }

    private fun regionCount(
        hsv: Mat,
        map: DiceGeometry.Mapping,
        bx0: Float, by0: Float, bx1: Float, by1: Float,
        lo: Scalar, hi: Scalar,
    ): Int {
        val x0 = map.x(bx0).roundToInt().coerceIn(0, hsv.cols() - 1)
        val x1 = map.x(bx1).roundToInt().coerceIn(x0 + 1, hsv.cols())
        val y0 = map.y(by0).roundToInt().coerceIn(0, hsv.rows() - 1)
        val y1 = map.y(by1).roundToInt().coerceIn(y0 + 1, hsv.rows())
        val roi = Mat(hsv, Rect(x0, y0, x1 - x0, y1 - y0))
        val m = Mat()
        try {
            Core.inRange(roi, lo, hi, m)
            return Core.countNonZero(m)
        } finally {
            m.release(); roi.release()
        }
    }
}
