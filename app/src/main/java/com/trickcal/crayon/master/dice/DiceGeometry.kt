package com.trickcal.crayon.master.dice

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

/** OpenCV 初始化。识图导入走 BoardScanner.init，骰子模块独立，必须自己保证一次。 */
internal object DiceOpenCv {
    @Volatile
    private var ok = false

    @Synchronized
    fun ensure(): Boolean {
        if (ok) return true
        ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return ok
    }
}

/**
 * 界面几何：把脚本在 1280×720 上标定的坐标换算到当前设备。
 *
 * 约束与蜡笔识图一致：入口先把画面缩到 [BASE_H] 高（[normalize]），宽度随设备。
 * 因此所有「按高度定义」的量（骰子面积、边长、墨迹数）保持原值可用，
 * 只有 x 方向需要换算 —— 这正是脚本里 30 多个 x 常量在别的设备上会失效的原因。
 *
 * 换算的锚点是计分板纸面矩形。它在两种已知配色（橙主题米色纸、蓝主题浅蓝纸）
 * 下都是画面里最亮的大块，用「V 通道高 + 列行投影」能稳定检出，
 * 而坐标随主题不变 —— 比硬编码某个采样点更可靠。
 *
 * 1280×720 上解算结果应为 sx≈1、ox≈0（恒等），这是 P1 的验收判据之一。
 */
object DiceGeometry {

    /** 归一化基准高度，与 BoardScanner.BASE_H 保持一致 */
    const val BASE_H = 720f

    /** 脚本标定时的画面宽度 */
    const val BASE_W = 1280f

    // 计分板纸面在 1280×720 下的实测边界（V>228 的列/行投影，6 张对局帧一致）
    const val BOARD_X0 = 23f
    const val BOARD_Y0 = 122f
    const val BOARD_X1 = 446f
    const val BOARD_Y1 = 685f

    // ---- 以下全部是脚本 config.py 的原始值（1280×720 坐标系）----

    /** 我方列采样范围；对方列是 307~430，不能越界 */
    const val SCORE_MY_X0 = 180f
    const val SCORE_MY_X1 = 306f

    /** 登记时点击的列中心（脚本 SCORE_COL2_X） */
    const val SCORE_COL_X = 238f

    /** 计分板行中心上下采样半径 */
    const val SCORE_ROW_DY = 20f

    /** 八行中心 y，下标与 [DiceCategory.solver] 一一对应 */
    val SCORE_ROWS = intArrayOf(162, 213, 264, 315, 366, 470, 522, 574)

    /** 桌面骰子搜索区 */
    const val TABLE_X0 = 640f
    const val TABLE_Y0 = 185f
    const val TABLE_X1 = 1035f
    const val TABLE_Y1 = 580f

    /** 保留槽：5 个槽心 x 与槽中心 y */
    val SLOT_XS = intArrayOf(684, 769, 853, 938, 1022)
    const val SLOT_Y = 125f
    const val SLOT_WIN_R = 34f

    /** 投掷圆盘与按钮 */
    const val BTN_DISC_X = 1235f
    const val BTN_DISC_Y = 360f
    const val BTN_REGISTER_X = 500f
    const val BTN_REGISTER_Y = 142f
    const val BTN_MAIN_X = 1035f
    const val BTN_MAIN_Y = 590f
    const val BTN_VICTORY_X = 1055f
    const val BTN_VICTORY_Y = 612f
    const val BTN_NEXT_X = 1190f
    const val BTN_NEXT_Y = 678f

    /** 左上角 N/8 计数器区域 */
    const val COUNTER_X0 = 38f
    const val COUNTER_Y0 = 52f
    const val COUNTER_X1 = 140f
    const val COUNTER_Y1 = 104f

    /** 退出确认弹窗的采样区域 */
    const val DLG_GREEN_X0 = 280f
    const val DLG_GREEN_Y0 = 100f
    const val DLG_GREEN_X1 = 800f
    const val DLG_GREEN_Y1 = 160f
    const val DLG_WHITE_X0 = 290f
    const val DLG_WHITE_Y0 = 200f
    const val DLG_WHITE_X1 = 790f
    const val DLG_WHITE_Y1 = 470f
    /** 弹窗『取消』按钮中心。只点取消，绝不点『结束』。 */
    const val DLG_CANCEL_X = 435f
    const val DLG_CANCEL_Y = 511f

    /** 检测置信度 */
    enum class Confidence {
        /** 计分板检出，映射可信 */
        HIGH,

        /** 没检出，退回恒等映射；1280×720 下仍正确，其他分辨率下不可信 */
        IDENTITY,
    }

    data class Board(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w: Int get() = x1 - x0
        val h: Int get() = y1 - y0
    }

    /**
     * 基准坐标 → 当前画面的线性换算（x/y 各自独立缩放，可吸收黑边、拉伸、等比居中）。
     * 不能吸收的是「元素相对位置重排」，那种情况必须靠检测器而不是这里的数学。
     */
    data class Mapping(val sx: Float, val ox: Float, val sy: Float, val oy: Float) {
        fun x(v: Float): Float = v * sx + ox
        fun y(v: Float): Float = v * sy + oy
        fun x(v: Int): Float = v * sx + ox
        fun y(v: Int): Float = v * sy + oy

        companion object {
            val IDENTITY = Mapping(1f, 0f, 1f, 0f)
        }
    }

    data class Solution(val mapping: Mapping, val board: Board?, val confidence: Confidence)

    /**
     * 把截图缩放到 [BASE_H] 高，宽度按比例（**不**强行压成 1280）：
     * 压宽度会破坏骰子的长宽比判定，也会让所有面积阈值失效。
     */
    fun normalize(src: Bitmap): Bitmap {
        if (src.height == BASE_H.toInt()) return src
        val w = (src.width * BASE_H / src.height).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, BASE_H.toInt(), true)
    }

    fun solve(normalized: Bitmap): Solution {
        if (!DiceOpenCv.ensure()) return Solution(Mapping.IDENTITY, null, Confidence.IDENTITY)
        val board = detectBoard(normalized) ?: return Solution(Mapping.IDENTITY, null, Confidence.IDENTITY)

        val sx = board.w / (BOARD_X1 - BOARD_X0)
        val sy = board.h / (BOARD_Y1 - BOARD_Y0)
        if (sx !in 0.5f..2.0f || sy !in 0.5f..2.0f) {
            return Solution(Mapping.IDENTITY, board, Confidence.IDENTITY)
        }
        val ox = board.x0 - BOARD_X0 * sx
        val oy = board.y0 - BOARD_Y0 * sy
        return Solution(Mapping(sx, ox, sy, oy), board, Confidence.HIGH)
    }

    /**
     * 计分板纸面检测：V 通道取高阈值（覆盖米色与浅蓝两种纸色），
     * 再做列/行投影；投影前做一维最大值平滑，否则纸面上的分隔线会把
     * 一整块计分板切成数段，最长段只到类目列（实测 x0=40..172，明显偏窄）。
     */
    private fun detectBoard(bmp: Bitmap): Board? {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val mask = Mat()
        try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Core.inRange(hsv, Scalar(0.0, 0.0, 228.0), Scalar(180.0, 255.0, 255.0), mask)

            // 计分板一定在左半屏，右侧同亮度的槽框/按钮必须排除
            val limit = (mask.cols() * 0.45f).roundToInt().coerceIn(1, mask.cols())
            mask.colRange(limit, mask.cols()).setTo(Scalar(0.0))

            val rows = mask.rows()
            val cols = mask.cols()
            val colSum = IntArray(cols)
            val buf = ByteArray(rows * cols)
            mask.get(0, 0, buf)
            for (y in 0 until rows) {
                val base = y * cols
                for (x in 0 until cols) if (buf[base + x].toInt() != 0) colSum[x]++
            }

            val colThr = (rows * 0.32f).toInt()
            val (cx0, cx1) = longestRun(smoothMax(colSum, 6)) { it > colThr }
            if (cx1 - cx0 < 50) return null

            // 行投影只在该列区间内进行
            val sub = mask.submat(0, rows, cx0, cx1)
            val wSub = cx1 - cx0
            val rowSum = IntArray(rows)
            val buf2 = ByteArray(rows * wSub)
            sub.get(0, 0, buf2)
            sub.release()
            for (y in 0 until rows) {
                val base = y * wSub
                var c = 0
                for (x in 0 until wSub) if (buf2[base + x].toInt() != 0) c++
                rowSum[y] = c
            }
            val rowThr = (wSub * 0.40f).toInt()
            val (cy0, cy1) = longestRun(smoothMax(rowSum, 6)) { it > rowThr }
            if (cy1 - cy0 < 50) return null

            return Board(cx0, cy0, cx1, cy1)
        } catch (_: Throwable) {
            return null
        } finally {
            rgba.release(); rgb.release(); hsv.release(); mask.release()
        }
    }

    /** 半径 r 的滑动最大值：把投影里被细分隔线打断的区间重新连起来 */
    private fun smoothMax(a: IntArray, r: Int): IntArray {
        val out = IntArray(a.size)
        for (i in a.indices) {
            var v = 0
            val lo = max(0, i - r)
            val hi = minOf(a.size - 1, i + r)
            for (j in lo..hi) if (a[j] > v) v = a[j]
            out[i] = v
        }
        return out
    }

    /** 返回满足条件的最长连续区间 [start, endExclusive] */
    private fun longestRun(a: IntArray, ok: (Int) -> Boolean): Pair<Int, Int> {
        var bestS = 0
        var bestE = 0
        var s = -1
        for (i in 0..a.size) {
            val good = i < a.size && ok(a[i])
            if (good && s < 0) s = i
            if (!good && s >= 0) {
                if (i - s > bestE - bestS) {
                    bestS = s
                    bestE = i
                }
                s = -1
            }
        }
        return bestS to bestE
    }
}
