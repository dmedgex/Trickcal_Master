package com.trickcal.crayon.master.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.trickcal.crayon.master.data.Attr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 识图导入流程：
 * 切换属性页 → 逐屏滑动 → 截图识别菱形 → OCR 认角色名 → 累加已解锁格子。
 *
 * 页面上「✕」表示该层没有此属性格，「数字」表示有但未解锁，只有「✓」需要录入。
 */
class ImportRunner(
    private val context: Context,
    private val knownNames: List<String>,
    private val onProgress: (Progress) -> Unit,
) {

    data class Progress(
        val attr: Attr?,
        val pageText: String,
        val cards: Int,
        val cells: Int,
        val finished: Boolean = false,
        val error: String? = null,
        val logs: List<String> = emptyList(),
    )

    private val logs = ArrayDeque<String>()

    private fun log(line: String) {
        android.util.Log.d("ImportRunner", line)
        logs.addLast(line)
        while (logs.size > 3) logs.removeFirst()
    }

    /** 属性 → 该属性已解锁的层集合。 */
    val result = mutableMapOf<String, MutableMap<Attr, MutableSet<Int>>>()

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    /** 五个属性页在左侧栏的相对位置（以屏幕高度为基准，来自实机标定）。 */
    private val pages = listOf(
        Attr.HEALTH to 0.278f,
        Attr.ATTACK to 0.375f,
        Attr.CRIT to 0.493f,
        Attr.CRIT_RESIST to 0.618f,
        Attr.DEFENSE to 0.722f,
    )

    suspend fun run(targets: List<Attr>) {
        val capture = ScreenCaptureService.instance
        if (capture == null) {
            onProgress(Progress(null, "", 0, 0, finished = true, error = "截屏服务未启动"))
            return
        }
        if (!TapService.isReady()) {
            onProgress(Progress(null, "", 0, 0, finished = true, error = "无障碍服务未开启"))
            return
        }

        val w = context.resources.displayMetrics.widthPixels
        val h = context.resources.displayMetrics.heightPixels

        for (attr in targets) {
            if (cancelled) break

            // 优先用 OCR 定位左侧栏文字再点击（与分辨率无关），失败再退回比例位置
            val tapped = tapAttrByText(attr, w, h)
            if (!tapped) {
                if (tapAttrByIcon(attr, w, h)) {
                    log("OCR 未找到「${attr.label}」，按图标位置点击")
                } else {
                    val y = pages.firstOrNull { it.first == attr }?.second?.times(h)
                    if (y == null) continue
                    log("图标也没找到「${attr.label}」，按比例点击")
                    TapService.tap(w * 0.08f, y)
                }
            } else {
                log("点击「${attr.label}」")
            }
            delay(1000)

            var totalCards = 0
            var totalCells = 0
            var prevBottomNames: List<String> = emptyList()
            var sameStreak = 0
            var screen = 0
            var lastDelta = 0f
            var rowStep = 0f   // 基准坐标系下的行间距，首屏测出后固定

            log("切换到 ${attr.label}")
            while (!cancelled) {
                val shot = capture.snapshot() ?: run { delay(300); null } ?: continue
                val cardList = BoardScanner.detect(shot)
                // 必须用同一帧做 OCR：另取一帧会因滚动/动画导致名字框与卡片错位
                val hits = recognizeNameBoxes(shot)
                // BoardScanner 内部按 720 高处理，名字框坐标换算到同一基准
                val k = BoardScanner.BASE_H / shot.height
                // 整图 OCR 会漏掉孤立单字（如「绫」），对没配上名字的卡片做局部放大 OCR
                val localNames = mutableMapOf<Int, String>()
                cardList.forEachIndexed { i, cd ->
                    val hasName = hits.any {
                        val cx = it.box.centerX() * k
                        val cy = it.box.centerY() * k
                        cx >= cd.rect.x && cx <= cd.rect.x + cd.rect.w &&
                            cy >= cd.rect.y && cy <= cd.rect.y + cd.rect.h
                    }
                    if (!hasName) ocrCardName(shot, cd.rect, k)?.let { localNames[i] = it }
                }
                shot.recycle()

                // 防止异常情况下无限滑动
                screen++
                if (screen > 30) {
                    log("达到单属性 30 屏上限，停止")
                    break
                }

                // 到底判定：本屏最下面两个角色名。滚动了名字就会换，到底后不变。
                val bottomNames = hits.sortedByDescending { it.box.centerY() }
                    .take(2).map { it.name }.sorted()
                if (bottomNames.isNotEmpty()) {
                    if (bottomNames == prevBottomNames) {
                        sameStreak++
                        log("底部未变（$sameStreak/3）：${bottomNames.joinToString("/")}")
                        if (sameStreak >= 3) {
                            log("已到底部，${attr.label} 完成")
                            break
                        }
                        delay(600)
                        continue
                    }
                    sameStreak = 0
                    prevBottomNames = bottomNames
                }

                // 归属靠几何包含：名字框中心落在哪张卡片矩形内就属于哪张卡
                var matched = 0
                cardList.forEachIndexed { idx, cd ->
                    val r = cd.rect
                    val name = hits.firstOrNull {
                        val cx = it.box.centerX() * k
                        val cy = it.box.centerY() * k
                        cx >= r.x && cx <= r.x + r.w && cy >= r.y && cy <= r.y + r.h
                    }?.name ?: localNames[idx] ?: return@forEachIndexed
                    matched++
                    val layers = cd.diamonds.mapNotNull { d ->
                        if (d.mark == BoardScanner.Mark.CHECK) cd.layerOf(d.cx) else null
                    }.toSet()
                    if (layers.isEmpty()) return@forEachIndexed
                    result.getOrPut(name) { mutableMapOf() }
                        .getOrPut(attr) { mutableSetOf() }
                        .addAll(layers)
                }
                android.util.Log.d(
                    "ImportRunner",
                    "attr=${attr.label} 卡片=${cardList.size} names=${hits.size} matched=$matched",
                )

                // 用去重后的口径，避免「82 卡」这种重复累加的假数字
                val hitChars = result.size
                val hitCells = result.values.sumOf { it[attr]?.size ?: 0 }
                log("第 $screen 屏 · $matched 卡 · 累计 ${hitChars} 人 / ${hitCells} 勾")
                onProgress(
                    Progress(
                        attr = attr,
                        pageText = "${attr.label} · 第 $screen 屏 $matched 卡 · ${hitChars} 人/${hitCells} 勾",
                        cards = hitChars,
                        cells = hitCells,
                        logs = logs.toList(),
                    ),
                )

                // 固定滑 3 行：距离取首屏实测行间距的 3 倍，保证滚动后内容与上次严格对齐
                val k2 = h / BoardScanner.BASE_H
                val stepPx = if (rowStep > 0f) rowStep * 3f * k2 else h * 0.375f
                val startY = h * 0.86f
                val endY = (startY - stepPx).coerceAtLeast(h * 0.10f)
                // 实际走的距离可能被系统截断（终点越界/触摸缩放），把计划值与真实终点打出来
                log(
                    "滑动 ${(startY - endY).toInt()}px" +
                        "（行距 ${rowStep.toInt()}×3）" +
                        if (startY - endY < stepPx - 1f) " 终点被截断" else "",
                )
                swipeHoldAndWait(w * 0.72f, startY, w * 0.72f, endY, 900, 1000)
                delay(250)
            }
        }

        onProgress(Progress(null, "识别完成", 0, 0, finished = true))
    }

    /** 一次 OCR 得到的「角色名 + 名字框」 */
    private data class NameHit(val name: String, val box: android.graphics.Rect)

    /** 卡片名字区局部 OCR：整图识别对孤立单字不可靠，裁出来放大 3 倍再识别 */
    /** 滑动并在手势结束后返回（TapService 的手势是回调式的，这里包成挂起函数） */
    private suspend fun swipeHoldAndWait(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        moveMs: Long,
        holdMs: Long,
    ) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            val ok = TapService.swipeHold(x1, y1, x2, y2, moveMs, holdMs) {
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
            }
            if (!ok && cont.isActive) cont.resumeWith(Result.success(Unit))
        }
    }

    private suspend fun ocrCardName(bmp: Bitmap, r: BoardScanner.CardRect, k: Float): String? {
        return try {
            val x0 = ((r.x + r.w * 0.08f) / k).toInt().coerceIn(0, bmp.width - 1)
            val y0 = ((r.y + r.h * 0.15f) / k).toInt().coerceIn(0, bmp.height - 1)
            val x1 = ((r.x + r.w * 0.48f) / k).toInt().coerceIn(x0 + 1, bmp.width)
            val y1 = ((r.y + r.h * 0.55f) / k).toInt().coerceIn(y0 + 1, bmp.height)
            val crop = Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
            val big = Bitmap.createScaledBitmap(crop, crop.width * 3, crop.height * 3, true)
            if (big !== crop) crop.recycle()
            val res = recognizer.process(InputImage.fromBitmap(big, 0)).await()
            if (big !== bmp) big.recycle()
            val raw = res.textBlocks.flatMap { it.lines }.map { it.text }.joinToString("")
            matchName(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun recognizeNameBoxes(bmp: Bitmap): List<NameHit> {
        val text = try {
            recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return emptyList()
        }
        val out = mutableListOf<NameHit>()
        text.textBlocks.flatMap { it.lines }.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            if (box.centerY() < bmp.height * 0.20f) return@forEach
            if (box.left < bmp.width * 0.12f) return@forEach
            val name = matchName(line.text) ?: return@forEach
            // 同一名字可能被切成多块，按位置就近合并
            if (out.any { it.name == name && abs(it.box.centerY() - box.centerY()) < 24 &&
                    abs(it.box.centerX() - box.centerX()) < 160 }) return@forEach
            out.add(NameHit(name, box))
        }
        return out
    }


    /**
     * 用 OCR 找到左侧栏里目标属性的文字并点击它的中心。
     * 映射：HP→生命值、攻击力→攻击力、暴击→「暴击与暴击伤害」、暴击抗性→「暴击抗性与暴击伤害抗性」、防御力→防御力。
     */
    private suspend fun tapAttrByText(attr: Attr, w: Int, h: Int): Boolean {
        val capture = ScreenCaptureService.instance ?: return false
        val bmp = capture.snapshot() ?: return false
        val text = try {
            recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        } catch (_: Throwable) {
            bmp.recycle()
            return false
        }
        bmp.recycle()

        val lines = text.textBlocks.flatMap { it.lines }
        var target: android.graphics.Rect? = null
        lines.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            // 左侧栏只占屏幕左边一小条；右侧标题（如「攻击力 388%」）必须排除
            if (box.centerX() > w * 0.25f) return@forEach
            if (line.text.any { it.isDigit() }) return@forEach
            if (attrOfText(line.text) != attr) return@forEach
            // 多个候选取最靠左的那个（左侧栏比内容区标题更靠左）
            if (target == null || box.left < target!!.left) target = box
        }
        val box = target ?: return false
        TapService.tap(box.centerX().toFloat(), box.centerY().toFloat())
        return true
    }

    /**
     * 按左侧栏图标位置点击：检测六个深绿图标圆的行中心，
     * 索引 0=全体使徒效果、1=HP、2=攻击力、3=暴击、4=暴击抗性、5=防御力。
     */
    private fun tapAttrByIcon(attr: Attr, w: Int, h: Int): Boolean {
        val capture = ScreenCaptureService.instance ?: return false
        val bmp = capture.snapshot() ?: return false
        val ix1 = maxOf(4, (w * 0.09f).toInt())
        val px = IntArray(ix1 * h)
        bmp.getPixels(px, 0, ix1, 0, 0, ix1, h)
        bmp.recycle()

        val deep = IntArray(h)
        for (y in 0 until h) {
            var cnt = 0
            val base = y * ix1
            for (x in 0 until ix1) {
                val c = px[base + x]
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
        val index = when (attr) {
            Attr.HEALTH -> 1
            Attr.ATTACK -> 2
            Attr.CRIT -> 3
            Attr.CRIT_RESIST -> 4
            Attr.DEFENSE -> 5
        }
        val cy = centers.getOrNull(index) ?: return false
        TapService.tap(w * 0.06f, cy.toFloat())
        return true
    }

    /** 文本 → 属性。注意「暴击抗性…」含「暴击」，必须先判抗性。 */
    private fun attrOfText(raw: String): Attr? {
        val t = raw.replace(" ", "").replace("\u3000", "")
        return when {
            t.contains("抗性") -> Attr.CRIT_RESIST
            t.contains("防御") -> Attr.DEFENSE
            t.contains("攻击") -> Attr.ATTACK
            t.contains("暴击") -> Attr.CRIT
            t.startsWith("HP") || t.contains("生命") -> Attr.HEALTH
            else -> null
        }
    }

    /** 在已知名字里找编辑距离最近的（容忍 OCR 少量错字）。 */
    private fun matchName(raw: String): String? = NameMatcher.match(raw, knownNames)


    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
}
