package com.trickcal.crayon.master.capture

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.trickcal.crayon.master.data.Attr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 静态截图识别：对用户导入的相册图片逐张识别，不依赖截屏服务与无障碍。
 * 与自动流程共用同一套 [BoardScanner] 与名字匹配规则。
 */
class StaticScanner(private val knownNames: List<String>) {

    data class CardHit(
        val name: String,
        val layers: Set<Int>,
        /** 三格的模板匹配分数，仅用于界面排查 */
        val scores: List<Float> = emptyList(),
    )

    data class ImageResult(
        val fileName: String,
        val cards: List<CardHit>,
        val rowCount: Int,
        val diamondCount: Int,
        val attr: Attr? = null,
        /** 本图 OCR 认出的角色名总数（含菱形全漏、无法归属的卡） */
        val nameCount: Int = 0,
        /** 调试视图：原图上画出识别用的框 */
        val annotated: android.graphics.Bitmap? = null,
    ) {
        val valid: Boolean get() = cards.isNotEmpty()
    }

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun scan(bitmap: Bitmap, fileName: String, debug: Boolean = false): ImageResult {
        // 检测单位是「卡片」：每张卡片自带右侧的三颗菱形，名字与菱形同属一张卡
        val cardList = BoardScanner.detect(bitmap)
        val diamondCount = cardList.sumOf { it.diamonds.size }

        val text = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return ImageResult(fileName, emptyList(), 0, diamondCount, detectAttr(bitmap))
        }
        val lines = text.textBlocks.flatMap { it.lines }
        // 诊断：把 OCR 每一行的原文和匹配结果都打出来，便于定位「卡片有框但没名字」的情况
        lines.forEach { l ->
            val b = l.boundingBox
            android.util.Log.d(
                "CardMatch",
                "OCR '${l.text}' box=(${b?.left},${b?.top},${b?.right},${b?.bottom}) → ${matchName(l.text)}",
            )
        }
        val nameCount = lines.count { matchName(it.text) != null }
        val attr = detectAttrByText(bitmap, lines) ?: detectAttr(bitmap)

        if (cardList.isEmpty()) {
            return ImageResult(fileName, emptyList(), 0, diamondCount, attr, nameCount)
        }

        val k = BoardScanner.BASE_H / bitmap.height
        val cards = mutableListOf<CardHit>()

        // 归属靠几何包含：名字框中心落在哪张卡片矩形内，就属于哪张卡。
        // 不再使用任何「向左/向右找最近名字」的距离阈值 —— 那是旧算法为了绕开
        // 自己定位不准而加的补丁，卡片矩形本身已经把归属说清楚了。
        cardList.forEach { cd ->
            val r = cd.rect
            var name = lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val cx = box.centerX() * k
                val cy = box.centerY() * k
                if (cx < r.x || cx > r.x + r.w) return@mapNotNull null
                if (cy < r.y || cy > r.y + r.h) return@mapNotNull null
                matchName(line.text)
            }.firstOrNull()

            // 整图 OCR 会漏掉孤立单字（如「绫」）。对没配上名字的卡片，
            // 把卡内左上角的名字区裁出来放大 3 倍单独识别一次。
            if (name == null) {
                name = ocrCardName(bitmap, r, k)
                if (name != null) {
                    android.util.Log.d("CardMatch", "  补识别：卡(${r.x},${r.y}) → $name")
                }
            }
            if (name == null) return@forEach

            val layers = cd.diamonds.mapNotNull { d ->
                if (d.mark == BoardScanner.Mark.CHECK) cd.layerOf(d.cx) else null
            }.toSet()
            val scores = cd.diamonds.map { it.score }
            android.util.Log.d(
                "CardMatch",
                "${name} 卡(${r.x},${r.y}) 菱形=${cd.diamonds.size} layers=$layers " +
                    "scores=${scores.map { "%.2f".format(it) }}",
            )
            cards.add(CardHit(name, layers, scores))
        }

        val annotated = if (debug) {
            drawDebug(bitmap, cardList.flatMap { it.diamonds }, cards, cardList.map { it.rect })
        } else null
        return ImageResult(fileName, cards, cardList.size, diamondCount, attr, nameCount, annotated)
    }

    /**
     * 卡片名字区的局部 OCR：整图识别对孤立单字不可靠，这里裁出名字区放大 3 倍再识别。
     * 名字区固定在卡片左侧、垂直居中偏上，取卡宽 8%~48%、卡高 15%~55%。
     */
    private suspend fun ocrCardName(bitmap: Bitmap, r: BoardScanner.CardRect, k: Float): String? {
        return try {
            val x0 = ((r.x + r.w * 0.08f) / k).toInt().coerceIn(0, bitmap.width - 1)
            val y0 = ((r.y + r.h * 0.15f) / k).toInt().coerceIn(0, bitmap.height - 1)
            val x1 = ((r.x + r.w * 0.48f) / k).toInt().coerceIn(x0 + 1, bitmap.width)
            val y1 = ((r.y + r.h * 0.55f) / k).toInt().coerceIn(y0 + 1, bitmap.height)
            val crop = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
            val big = Bitmap.createScaledBitmap(crop, crop.width * 3, crop.height * 3, true)
            if (big !== crop) crop.recycle()
            val res = recognizer.process(InputImage.fromBitmap(big, 0)).await()
            if (big !== bitmap) big.recycle()
            val raw = res.textBlocks.flatMap { it.lines }.map { it.text }.joinToString("")
            android.util.Log.d("CardMatch", "  局部OCR 卡(${r.x},${r.y}) 原文='$raw'")
            matchName(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w("CardMatch", "局部OCR 失败", t)
            null
        }
    }

    /**
     * 用左侧栏文字判断当前属性页：OCR 找到「HP / 攻击力 / 暴击… / 防御力」这些文本块，
     * 再比较各自右侧背景的亮度——选中项背后是深绿胶囊，明显更暗。
     * 全程只用文字位置，不依赖任何像素比例，换分辨率也不会错位。
     */
    private fun detectAttrByText(bitmap: Bitmap, lines: List<com.google.mlkit.vision.text.Text.Line>): Attr? {
        val w = bitmap.width
        val h = bitmap.height
        val cands = mutableListOf<Pair<Attr, Rect>>()
        lines.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            // 属性词条都在左侧栏
            if (box.centerX() > w * 0.35f) return@forEach
            if (box.width() > w * 0.32f) return@forEach
            val a = attrOfText(line.text) ?: return@forEach
            cands.add(a to box)
        }
        if (cands.size < 3) return null

        var best: Attr? = null
        var bestSpan = -1
        var secondSpan = -1
        cands.forEach { (a, box) ->
            val span = darkGreenSpanOf(bitmap, box, w, h)
            if (span > bestSpan) {
                secondSpan = bestSpan
                bestSpan = span
                best = a
            } else if (span > secondSpan) {
                secondSpan = span
            }
        }
        // 选中行的深绿胶囊横向很长（图标一直延到文字末尾），未选中行只有图标圆
        if (bestSpan - secondSpan < 30) {
            android.util.Log.d("StaticScan", "attrByText 区分度不足 best=$bestSpan second=$secondSpan")
            return null
        }
        android.util.Log.d("StaticScan", "attrByText cands=${cands.size} span=$bestSpan -> $best")
        return best
    }

    /**
     * 与该文字同一水平线上，深绿（胶囊色）像素的横向跨度。
     * 选中行的胶囊从图标一直延伸到文字末尾，跨度很长；未选中行只有图标那个圆，跨度很短。
     * 用跨度而不是占比：图标本身也是深绿，会让各行占比被拉平。
     */
    private fun darkGreenSpanOf(bmp: Bitmap, box: Rect, w: Int, h: Int): Int {
        val x0 = (w * 0.02f).toInt().coerceIn(0, w - 1)
        val x1 = (box.right + (w * 0.035f).toInt()).coerceIn(x0 + 1, w)
        val cy = box.centerY().coerceIn(2, h - 3)
        val pw = x1 - x0
        val ph = 5
        val y0 = cy - 2
        val px = IntArray(pw * ph)
        bmp.getPixels(px, 0, pw, x0, y0, pw, ph)

        var minX = -1
        var maxX = -1
        for (col in 0 until pw) {
            var hit = false
            for (row in 0 until ph) {
                val c = px[row * pw + col]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val v = maxOf(r, maxOf(g, b))
                if (g > r + 15 && g > b + 15 && v < 160) {
                    hit = true
                    break
                }
            }
            if (hit) {
                if (minX < 0) minX = col
                maxX = col
            }
        }
        return if (minX >= 0) maxX - minX else 0
    }

    /** 文本 → 属性。注意「暴击抗性…」含「暴击」，必须先判抗性。 */
    private fun attrOfText(raw: String): Attr? {
        val t = raw.replace(" ", "").replace("　", "")
        return when {
            t.contains("抗性") -> Attr.CRIT_RESIST
            t.contains("防御") -> Attr.DEFENSE
            t.contains("攻击") -> Attr.ATTACK
            t.contains("暴击") -> Attr.CRIT
            t.startsWith("HP") || t.contains("生命") -> Attr.HEALTH
            else -> null
        }
    }

    /**
     * 识别当前截图对应哪个属性页。
     *
     * 两步：
     * 1. 选中项在左侧栏多出一条深绿胶囊，表现为「文字区整行亮度骤降」（实测 104 vs 其他行 180）；
     * 2. 侧栏六项的纵向间距并不均匀（不同分辨率/换行会变），所以不能按高度等分。
     *    改为检测六个深绿图标圆的行位置，取与选中行最近的那个。
     */
    fun detectAttr(bitmap: Bitmap): Attr? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 200 || h < 200) return null

        // 1) 图标行剖面：只扫最左侧 9% 宽
        val ix1 = maxOf(4, (w * 0.09f).toInt())
        val iconPx = IntArray(ix1 * h)
        bitmap.getPixels(iconPx, 0, ix1, 0, 0, ix1, h)
        val deep = IntArray(h)
        for (y in 0 until h) {
            var cnt = 0
            val base = y * ix1
            for (x in 0 until ix1) {
                val c = iconPx[base + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (g > 110 && g > r + 25 && g > b + 25 && g < 200) cnt++
            }
            deep[y] = cnt
        }
        val centers = ArrayList<Int>(8)
        var segStart = -1
        for (y in 0..h) {
            val thick = y < h && deep[y] > 15
            if (thick) {
                if (segStart < 0) segStart = y
            } else {
                if (segStart >= 0 && y - segStart > 18) centers.add((segStart + y) / 2)
                segStart = -1
            }
        }

        // 2) 选中行：文字区（11%~19% 宽）逐行亮度最暗处
        val x0 = (w * 0.11f).toInt().coerceIn(0, w - 1)
        val x1 = (w * 0.19f).toInt().coerceIn(x0 + 1, w)
        val yTop = (h * 0.14f).toInt().coerceIn(0, h - 1)
        val yBot = (h * 0.97f).toInt().coerceIn(yTop + 1, h)
        val rw = x1 - x0
        val rh = yBot - yTop
        if (rw < 4 || rh < 40) return null

        val px = IntArray(rw * rh)
        bitmap.getPixels(px, 0, rw, x0, yTop, rw, rh)
        val rows = FloatArray(rh)
        for (y in 0 until rh) {
            var sum = 0f
            val base = y * rw
            for (x in 0 until rw) {
                val c = px[base + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += ((r * 77 + g * 151 + b * 28) shr 8).toFloat()
            }
            rows[y] = sum / rw
        }
        val smooth = FloatArray(rh)
        for (y in 0 until rh) {
            var acc = 0f
            var cnt = 0
            for (k in -2..2) {
                val yy = y + k
                if (yy in 0 until rh) { acc += rows[yy]; cnt++ }
            }
            smooth[y] = acc / cnt
        }
        var bestY = 0
        var bestV = Float.MAX_VALUE
        for (y in 5 until rh - 5) {
            if (smooth[y] < bestV) { bestV = smooth[y]; bestY = y }
        }
        val sorted = rows.clone()
        sorted.sort()
        val median = sorted[sorted.size / 2]
        if (median - bestV < 25f) return null
        val selY = yTop + bestY

        // 3) 最近邻匹配图标
        if (centers.size >= 4) {
            var best = 0
            var bestD = Int.MAX_VALUE
            centers.forEachIndexed { i, cy ->
                val d = abs(cy - selY)
                if (d < bestD) { bestD = d; best = i }
            }
            return attrOfIndex(best)
        }

        // 4) 退化：六等分
        val rel = bestY.toFloat() / rh
        return attrOfIndex(Math.round(rel * 6 - 0.5f).toInt().coerceIn(0, 5))
    }

    private fun attrOfIndex(index: Int): Attr? = when (index) {
        1 -> Attr.HEALTH
        2 -> Attr.ATTACK
        3 -> Attr.CRIT
        4 -> Attr.CRIT_RESIST
        5 -> Attr.DEFENSE
        else -> null   // 0 = 全体使徒效果，该页没有格子数据
    }

    /**
     * 调试视图：在原图上画出识别用到的框，方便肉眼核对位置对不对。
     * 绿框=判成勾，灰框=未判勾，黄线=行基线。
     */
    private fun drawDebug(
        src: Bitmap,
        diamonds: List<BoardScanner.Diamond>,
        cards: List<CardHit>,
        rects: List<BoardScanner.CardRect> = emptyList(),
    ): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        val k = src.height / BoardScanner.BASE_H
        val pass = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = maxOf(2f, src.width / 640f)
            isAntiAlias = true
        }
        val text = android.graphics.Paint().apply {
            textSize = maxOf(18f, src.width / 64f)
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#1E88E5")
        }
        val r = 24f * k

        // 卡片矩形（蓝框）
        pass.color = android.graphics.Color.parseColor("#1E88E5")
        rects.forEach { r ->
            canvas.drawRect(r.x * k, r.y * k, (r.x + r.w) * k, (r.y + r.h) * k, pass)
        }

        // 只画判成勾的框，非勾不画（避免和游戏自带的菱形灰槽混淆）
        pass.color = android.graphics.Color.parseColor("#2ECC40")
        diamonds.filter { it.mark == BoardScanner.Mark.CHECK }.forEach { d ->
            canvas.drawRect(d.cx * k - r, d.cy * k - r, d.cx * k + r, d.cy * k + r, pass)
        }

        cards.forEachIndexed { i, hit ->
            val label = "${hit.name} " + hit.layers.joinToString("/", "[", "]")
            canvas.drawText(label, 12f, src.height - 20f - i * text.textSize, text)
        }
        return out
    }

    /** 已知名字里取编辑距离最近的，容忍 OCR 少量错字。 */
    private fun matchName(raw: String): String? = NameMatcher.match(raw, knownNames)


    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
