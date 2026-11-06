package com.trickcal.crayon.master.dice

import android.graphics.Bitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 挂机流程与宿主环境的边界。
 * 抽成接口有两个好处：Runner 不直接依赖 Android 服务，逻辑可以单独推演；
 * 单元测试里也能用假实现驱动整局流程，不必上设备。
 */
interface DiceHost {
    /** 当前屏幕帧的副本；拿不到返回 null。调用方负责 recycle。 */
    fun snapshot(): Bitmap?

    /** 点击真实屏幕坐标（不是 720 基准坐标）。 */
    fun tap(x: Float, y: Float): Boolean

    fun nowMs(): Long
}

data class DiceSettings(
    /** 目标局数，脚本默认 80（每日入场次数上限） */
    val maxGames: Int = 80,
    /** 启动时已玩局数 */
    val startPlayed: Int = 0,
)

data class DiceState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val scene: String = "-",
    val round: String = "-",
    val dice: String = "-",
    val used: String = "-",
    val played: Int = 0,
    val maxGames: Int = 80,
    val window: String = "-",
    val upper: String = "0/55",
    val anchorOk: Boolean = true,
    val logs: List<String> = emptyList(),
)

/**
 * 全自动挂机状态机，逐函数移植自 bot.py。
 *
 * 与原脚本的两处刻意差异：
 * 1. 骰子构成不是「4 橙 + 1 绿」时**暂停**而不是回退启发式。识别出错还继续点，
 *    代价是登记错格子直接丢分；停下来等人看一帧更划算。
 * 2. 睡眠按 100ms 切片，暂停请求最迟 100ms 内生效（脚本是 0.1s 轮询，等价）。
 *
 * 时间模型与脚本一致：**每点一次圆盘就开启一个 14 秒窗口**，骰子旋转期间也在计时。
 */
class DiceRunner(
    private val host: DiceHost,
    private val policy: DicePolicy,
    private val settings: DiceSettings = DiceSettings(),
) {

    private companion object {
        const val WAIT_TAP = 700L
        const val WAIT_ROLL = 1200L
        const val SETTLE_GAP = 300L
        const val SETTLE_TIMEOUT = 4500L
        const val SETTLE_NEED = 2
        const val SETTLED_Y_SPREAD = 60f
        const val MATCH_TIMEOUT = 40_000L
        const val UNKNOWN_LIMIT = 12_000L
        const val UNKNOWN_LIMIT_2 = 20_000L
        const val ROLL_LIMIT = 14_000L
        const val MAX_ENTER_FAIL = 3
        const val UNKNOWN_UPPER_ESTIMATE = 8
        const val OCC_MISS_TOLERANCE = 2
        const val DLG_MAX_CANCEL = 4
        const val LOG_KEEP = 6
    }

    private val _state = MutableStateFlow(
        DiceState(maxGames = settings.maxGames, played = settings.startPlayed),
    )
    val state: StateFlow<DiceState> = _state

    @Volatile
    var pauseRequested: Boolean = false

    private val logs = ArrayDeque<String>()

    // ---- 本局状态（对应脚本的实例字段）----
    private val used = LinkedHashSet<DiceCategory>()
    private var upperSum = 0
    private var roundNum: Int? = null
    private var roundText = "-"
    private var missingCount = HashMap<DiceCategory, Int>()
    private val estPoints = HashMap<DiceCategory, Int>()

    // ---- 运行时 ----
    private var played = settings.startPlayed
    private var mapping = DiceGeometry.Mapping.IDENTITY
    private var scale = 1f
    private var rollStartMs: Long? = null
    private var unknownSince: Long? = null
    private var enterFail = 0
    private var dlgHits = 0
    private var cancelClicks = 0

    private fun log(msg: String) {
        val line = msg.trim()
        logs.addLast(line)
        while (logs.size > LOG_KEEP) logs.removeFirst()
        _state.value = _state.value.copy(logs = logs.toList())
    }

    private fun publish() {
        _state.value = _state.value.copy(
            paused = pauseRequested,
            played = played,
            round = roundText,
            upper = "$upperSum/55",
            used = if (used.isEmpty()) "无" else used.joinToString("、") { it.label },
            running = true,
        )
    }

    // ------------------------------------------------------------------ 基础

    /** 暂停期间不推进，但保留可取消性：协程 cancel 会在这里直接抛出 */
    private suspend fun checkpoint() {
        while (pauseRequested) delay(150)
    }

    /** 按 100ms 切片睡眠，让暂停请求能及时生效 */
    private suspend fun wait(ms: Long) {
        var left = ms
        while (left > 0) {
            checkpoint()
            val step = if (left > 100) 100L else left
            delay(step)
            left -= step
        }
    }

    private class Frame(
        val info: FrameInfo,
        /** 归一化后是否是新位图（新位图需要回收） */
        val recycledTarget: Bitmap?,
        /** 原始截图，调用方负责回收 */
        val raw: Bitmap,
    )

    /**
     * 取一帧并识别。返回的 [Frame.raw] 必须由调用方回收；
     * 归一化位图若与原图不同，在这里已经记下待回收引用。
     */
    private suspend fun grab(solveMapping: Boolean = false): Frame? {
        val raw = host.snapshot() ?: return null
        val norm = DiceGeometry.normalize(raw)
        scale = raw.height / DiceGeometry.BASE_H
        if (solveMapping) {
            val sol = DiceGeometry.solve(norm)
            mapping = sol.mapping
            _state.value = _state.value.copy(anchorOk = sol.confidence == DiceGeometry.Confidence.HIGH)
            if (sol.confidence == DiceGeometry.Confidence.IDENTITY) {
                log("⚠ 计分板锚点未检出，按恒等映射继续（仅 1280×720 可信）")
            }
        }
        val info = DiceVision.analyze(norm, mapping)
        return Frame(info, if (norm !== raw) norm else null, raw)
    }

    private fun Frame.close() {
        recycledTarget?.recycle()
        raw.recycle()
    }

    private fun tapBase(bx: Float, by: Float, waitMs: Long = WAIT_TAP) {
        val x = mapping.x(bx) * scale
        val y = mapping.y(by) * scale
        host.tap(x, y)
        log("  点击 (${x.toInt()},${y.toInt()})")
        // 调用方通常在之后 sleep，这里不阻塞
        if (waitMs <= 0) return
    }

    private suspend fun tapAndWait(bx: Float, by: Float, waitMs: Long = WAIT_TAP) {
        tapBase(bx, by)
        wait(waitMs)
    }

    private fun rollLeft(): Long? {
        val t0 = rollStartMs ?: return null
        return ROLL_LIMIT - (host.nowMs() - t0)
    }

    private suspend fun startRoll() {
        rollStartMs = host.nowMs()
        log("  点击圆盘投掷（本窗口限时 ${ROLL_LIMIT / 1000}s）")
        tapBase(DiceGeometry.BTN_DISC_X, DiceGeometry.BTN_DISC_Y)
        wait(WAIT_ROLL)
    }

    // ------------------------------------------------------------------ 场景等待

    private suspend fun waitScene(
        targets: Set<DiceScene>,
        timeout: Long,
        confirm: Int = 2,
    ): DiceScene? {
        val t0 = host.nowMs()
        var hit: DiceScene? = null
        var n = 0
        var last: DiceScene = DiceScene.UNKNOWN
        while (host.nowMs() - t0 < timeout) {
            val f = grab() ?: run { wait(200); null } ?: continue
            last = f.info.scene
            f.close()
            _state.value = _state.value.copy(scene = sceneName(last))
            if (last in targets) {
                if (last == hit) n++ else {
                    hit = last
                    n = 1
                }
                if (n >= confirm) return last
            } else {
                hit = null
                n = 0
            }
            wait(400)
        }
        log("  等待场景 $targets 超时，当前=${sceneName(last)}")
        return null
    }

    // ------------------------------------------------------------------ 主循环

    suspend fun run() {
        _state.value = _state.value.copy(running = true, maxGames = settings.maxGames)
        log("开始挂机：已玩 $played 局，目标 ${settings.maxGames}")
        var needMapping = true

        while (played < settings.maxGames) {
            checkpoint()
            val f = grab(solveMapping = needMapping) ?: run {
                wait(300)
                null
            } ?: continue
            val info = f.info
            f.close()
            needMapping = false

            _state.value = _state.value.copy(
                scene = sceneName(info.scene),
                round = roundText,
                played = played,
            )

            if (info.scene == DiceScene.MATCH) needMapping = false

            when (info.scene) {
                DiceScene.DIALOG -> {
                    handleDialog()
                }

                DiceScene.UNKNOWN -> {
                    handleUnknown()
                }

                DiceScene.MAIN -> {
                    unknownSince = null
                    log("主界面，开启第 ${played + 1} 局")
                    tapAndWait(DiceGeometry.BTN_MAIN_X, DiceGeometry.BTN_MAIN_Y, 1000)
                    val s = waitScene(setOf(DiceScene.MATCH), MATCH_TIMEOUT)
                    if (s == null) {
                        enterFail++
                        log("  点击后未进入对局 ($enterFail/$MAX_ENTER_FAIL)")
                        if (enterFail >= MAX_ENTER_FAIL) {
                            enterFail = 0
                            log("  连续 $MAX_ENTER_FAIL 次无法进入对局，可能今日入场次数已用完，暂停")
                            pauseRequested = true
                            publish()
                        }
                    } else {
                        enterFail = 0
                        resetRound()
                        needMapping = true
                    }
                }

                DiceScene.MATCH -> {
                    unknownSince = null
                    if (!info.isMyTurn) {
                        wait(1200)
                        continue
                    }
                    if (used.size >= 8) {
                        wait(1500)
                        continue
                    }
                    log("我方回合（第 ${used.size + 1}/8 回合）")
                    if (!playTurn()) {
                        log("  本回合未完成，重试")
                        wait(1000)
                    }
                }

                DiceScene.RESULT -> {
                    unknownSince = null
                    log("结算界面，点下一步")
                    tapAndWait(DiceGeometry.BTN_NEXT_X, DiceGeometry.BTN_NEXT_Y, 1500)
                }

                DiceScene.VICTORY -> {
                    unknownSince = null
                    played++
                    log("对局结束（第 $played/${settings.maxGames} 局），开下一把")
                    wait(1000)
                    tapAndWait(DiceGeometry.BTN_VICTORY_X, DiceGeometry.BTN_VICTORY_Y, 1500)
                    waitScene(setOf(DiceScene.MATCH), MATCH_TIMEOUT)
                    resetRound()
                    needMapping = true
                    publish()
                }
            }
            wait(400)
        }
        log("已刷满 ${settings.maxGames} 局，退出")
        _state.value = _state.value.copy(running = false, paused = false)
    }

    private fun resetRound() {
        used.clear()
        upperSum = 0
        roundNum = null
        roundText = "-"
        missingCount.clear()
        estPoints.clear()
        _state.value = _state.value.copy(used = "无", round = "-", upper = "0/55")
    }

    // ------------------------------------------------------------------ 异常

    private suspend fun handleDialog() {
        dlgHits++
        if (dlgHits < 2) return
        if (cancelClicks >= DLG_MAX_CANCEL) {
            log("!! 已点 $cancelClicks 次『取消』弹窗仍在，暂停等人工处理")
            pauseRequested = true
            publish()
            return
        }
        cancelClicks++
        dlgHits = 0
        log("检测到『要结束游戏吗?』弹窗，点『取消』返回游戏（第 $cancelClicks/$DLG_MAX_CANCEL 次）")
        tapAndWait(DiceGeometry.DLG_CANCEL_X, DiceGeometry.DLG_CANCEL_Y, 1000)
    }

    /**
     * 未知界面不按返回键 —— 那会弹出「要结束游戏吗?」确认框，把整个流程卡死。
     * 宽限 12 秒等界面自己恢复，仍不恢复就暂停等人。
     */
    private suspend fun handleUnknown() {
        val now = host.nowMs()
        if (unknownSince == null) {
            unknownSince = now
            return
        }
        val dt = now - unknownSince!!
        if (dt < UNKNOWN_LIMIT) return
        if (dt < UNKNOWN_LIMIT_2) return
        log("!! 未知界面持续 ${dt / 1000}s，暂停等人工处理")
        unknownSince = null
        pauseRequested = true
        publish()
    }

    // ------------------------------------------------------------------ 一回合

    private suspend fun playTurn(): Boolean {
        var rollsLeft = 2
        rollStartMs = null
        syncBoard("回合开始")
        if (used.size >= 8) {
            log("  8 个格子已全部登记，等待结算")
            wait(2000)
            return true
        }
        startRoll()

        repeat(40) {
            _state.value = _state.value.copy(
                round = "${used.size}/8",
                dice = "-",
            )
            val r = readSettled(rollsLeft)
            if (r.scene != DiceScene.MATCH) {
                log("  回合已切换(${sceneName(r.scene)})")
                syncBoard("回合结束")
                return true
            }
            if (r.table == null) {
                log("  未能确认停稳，重新读取")
                val f2 = grab()
                if (f2 != null) {
                    val empty = f2.info.table.isEmpty() && f2.info.slots.isEmpty()
                    f2.close()
                    if (empty) {
                        log("  桌面无骰子")
                        startRoll()
                    }
                }
                return@repeat
            }

            syncBoard("决策前", r.info)

            val diceList = r.info.allDice
            val left = rollLeft()
            _state.value = _state.value.copy(
                window = if (left == null) "-" else "${left / 1000.0}s",
                dice = diceList.joinToString(" ") { fmtDie(it) },
            )
            log(
                "识别: 桌上[${r.info.table.joinToString(" ") { fmtDie(it) }.ifEmpty { "空" }}] " +
                    "槽内[${r.info.slots.joinToString(" ") { fmtDie(it) }.ifEmpty { "空" }}] " +
                    "| 剩余重投$rollsLeft | 上级总和$upperSum/55 | 已登记[" +
                    (if (used.isEmpty()) "无" else used.joinToString("、") { it.label }) + "]",
            )

            val decision = policy.decide(diceList.map { it.toDie() }, rollsLeft, used, upperSum)
            if (decision == null) {
                log("!! 骰子构成异常（应为 4 颗橙骰 + 1 颗绿骰），暂停等人工核对")
                pauseRequested = true
                publish()
                return false
            }

            when (decision) {
                is Decision.Register -> {
                    log("决策: 登记 · 理由: ${decision.reason}")
                    return doRegister(decision.category, decision.points)
                }

                is Decision.Reroll -> {
                    if (rollsLeft <= 0) {
                        // 查表在 rerolls=0 时只会给登记，这里是保险：用同一张表再问一次
                        val forced = policy.decide(diceList.map { it.toDie() }, 0, used, upperSum)
                        if (forced is Decision.Register) {
                            return doRegister(forced.category, forced.points)
                        }
                        log("!! 无可登记方案，暂停")
                        pauseRequested = true
                        publish()
                        return false
                    }
                    val keepSet = decision.keepIndexes.toSet()
                    // 槽里的骰子若不在保留集合内，要先点回桌上，才能一起重投
                    val releaseSlots = r.info.slots.filterIndexed { i, _ -> i !in keepSet }
                    val keepTable = r.info.table.filterIndexed { i, _ ->
                        (i + r.info.slots.size) in keepSet
                    }

                    log("决策: 重投 · 理由: ${decision.reason}")
                    log(
                        "  执行: 保留 [${keepTable.joinToString("、") { fmtDie(it) }.ifEmpty { "无" }}]" +
                            (if (releaseSlots.isEmpty()) "" else
                                "，取回 [${releaseSlots.joinToString("、") { fmtDie(it) }}]") +
                            " · 重投 ${5 - decision.keepIndexes.size} 颗 (剩余重投 $rollsLeft->${rollsLeft - 1})",
                    )
                    if (releaseSlots.isNotEmpty()) releaseDice(releaseSlots)
                    if (keepTable.isNotEmpty()) {
                        keepDice(keepTable, expectSlots = r.info.slots.size - releaseSlots.size + keepTable.size)
                    }

                    // 重投前先确认仍是我方回合，避免对着对方回合乱点
                    val probe = grab()
                    val stillMine = probe?.info?.scene == DiceScene.MATCH && probe.info.isMyTurn
                    probe?.close()
                    if (!stillMine) {
                        log("  回合已结束（可能超时被自动登记），交回主循环处理")
                        syncBoard("重投前发现回合已结束")
                        return true
                    }
                    startRoll()
                    rollsLeft -= 1
                }
            }
        }
        return false
    }

    private class Settled(
        val info: FrameInfo,
        val table: List<DetectedDie>?,
        val scene: DiceScene,
    )

    /**
     * 等骰子停稳再采信：桌上+槽内恰好 5 颗、桌上排成一行（y 跨度 ≤60px）、
     * 连续两帧指纹完全一致。动画帧会读到 6 颗或位置散乱，遮挡帧会少颗，都被拒绝。
     */
    private suspend fun readSettled(rollsLeft: Int): Settled {
        val left = rollLeft()
        val timeout = if (left == null) SETTLE_TIMEOUT
        else maxOf(1000L, minOf(SETTLE_TIMEOUT, left - 2000))
        val t0 = host.nowMs()
        var prev: List<Any>? = null
        var stable = 0
        var last: FrameInfo? = null

        while (host.nowMs() - t0 < timeout) {
            val f = grab() ?: run { wait(SETTLE_GAP); null } ?: continue
            val info = f.info
            f.close()
            last = info
            _state.value = _state.value.copy(scene = sceneName(info.scene))
            if (info.scene != DiceScene.MATCH) return Settled(info, null, info.scene)

            val table = info.table
            val slots = info.slots
            val ys = table.map { it.y }
            val spread = if (ys.isEmpty()) 0f else ys.max() - ys.min()
            val sig = fingerprint(table + slots)

            // 桌空只在最后一次投掷后合法（游戏会把骰子全部收进槽）
            if (table.isEmpty() && rollsLeft != 0) {
                prev = sig
                stable = 0
                wait(SETTLE_GAP)
                continue
            }
            val need = SETTLE_NEED + if (table.isEmpty()) 1 else 0
            if (table.size + slots.size == 5 && spread <= SETTLED_Y_SPREAD && sig == prev) {
                stable++
                if (stable >= need - 1) {
                    _state.value = _state.value.copy(dice = (slots + table).joinToString(" ") { fmtDie(it) })
                    return Settled(info, table, info.scene)
                }
            } else {
                stable = 0
            }
            prev = sig
            wait(SETTLE_GAP)
        }
        last?.let { log("  等骰子停稳超时(${timeout}ms): 桌${it.table.size}槽${it.slots.size}") }
        return Settled(last ?: FrameInfo(DiceScene.UNKNOWN), null, last?.scene ?: DiceScene.UNKNOWN)
    }

    /** 位置量化为 8px 网格的指纹，用于判断帧间是否完全一致 */
    private fun fingerprint(dice: List<DetectedDie>): List<Any> =
        dice.map { listOf(it.kind, (it.x / 8).toInt(), (it.y / 8).toInt(), it.value ?: -1) }
            .sortedBy { it.toString() }

    // ------------------------------------------------------------------ 保留 / 取回

    /** 轻量读数：单帧，只校验 5 颗 + 排成一行。保留过程中骰子已静止，无需多帧确认。 */
    private suspend fun readQuick(tries: Int = 3): Pair<List<DetectedDie>?, List<DetectedDie>?> {
        var result: Pair<List<DetectedDie>?, List<DetectedDie>?> = null to null
        repeat(tries) {
            checkpoint()
            val f = grab() ?: return@repeat
            val info = f.info
            f.close()
            if (info.scene != DiceScene.MATCH) return null to null
            val ys = info.table.map { it.y }
            val spread = if (ys.isEmpty()) 0f else ys.max() - ys.min()
            result = info.table to info.slots
            if (info.table.size + info.slots.size == 5 && spread <= SETTLED_Y_SPREAD) return result
            wait(250)
        }
        return result
    }

    private suspend fun releaseDice(dice: List<DetectedDie>) {
        for (d in dice) {
            log("  取回保留槽骰子: ${fmtDie(d)} @(${d.x.toInt()},${d.y.toInt()})")
            host.tap(d.x * scale, d.y * scale)
            wait(500)
        }
    }

    /**
     * 逐颗点击保留。点掉一颗后桌上剩余骰子会自动靠拢重排，所以每颗都要重新识别定位；
     * 判定依据是保留槽数量 +1，不能靠位置（同点数的另一颗会被重排到同一位置）。
     */
    private suspend fun keepDice(dice: List<DetectedDie>, expectSlots: Int): Boolean {
        var done = 0
        for ((i, target) in dice.withIndex()) {
            var placed = false
            for (attempt in 0 until 3) {
                checkpoint()
                val (table, slots) = readQuick()
                if (table == null) continue
                val before = slots?.size ?: 0
                val cand = table.filter { it.kind == target.kind && it.value == target.value }
                if (cand.isEmpty()) {
                    log("  第${i + 1}/${dice.size}颗 ${fmtDie(target)} 已不在桌上（已进保留槽）")
                    placed = true
                    break
                }
                val d = cand.minByOrNull {
                    kotlin.math.abs(it.x - target.x) + kotlin.math.abs(it.y - target.y)
                } ?: continue
                val left = rollLeft()
                log(
                    "  ${if (attempt > 0) "重试点击" else "保留"}第${i + 1}/${dice.size}颗: " +
                        "${fmtDie(d)} @(${d.x.toInt()},${d.y.toInt()})" +
                        "（窗口剩余 ${if (left == null) "-" else "${left / 1000.0}s"}）",
                )
                host.tap(d.x * scale, d.y * scale)
                wait(500)
                val f = grab()
                val after = f?.info?.slots?.size ?: before
                val mine = f?.info?.scene == DiceScene.MATCH && f.info.isMyTurn
                f?.close()
                if (!mine) {
                    log("!! 保留过程中回合已切换，停止保留")
                    return done > 0
                }
                if (after > before) {
                    done++
                    placed = true
                    break
                }
                log("  !! 点击未生效（保留槽未增加），重试")
            }
            if (!placed) log("  第${i + 1}/${dice.size}颗 未进槽，继续下一颗")
        }
        if (done < dice.size) log("  保留结束: 成功 $done/${dice.size} 颗（目标槽位 $expectSlots）")
        return done > 0
    }

    // ------------------------------------------------------------------ 登记

    private suspend fun doRegister(cat: DiceCategory, points: Int): Boolean {
        for (attempt in 0 until 2) {
            val y = DiceGeometry.SCORE_ROWS[cat.solver].toFloat()
            log(
                "  登记操作: 点计分板『${cat.label}』行(" +
                    "${DiceGeometry.SCORE_COL_X.toInt()},${y.toInt()}) -> 点『登记组合』",
            )
            tapAndWait(DiceGeometry.SCORE_COL_X, y, 500)
            tapAndWait(DiceGeometry.BTN_REGISTER_X, DiceGeometry.BTN_REGISTER_Y, 800)

            // 成功判据沿用脚本的双重确认：场景离开对局，或计分板该格已出现数字
            wait(600)
            val f = grab()
            val scene = f?.info?.scene ?: DiceScene.UNKNOWN
            val occ = f?.info?.occupancy ?: emptyMap()
            val stillMine = f?.info?.isMyTurn == true
            f?.close()
            val leftRound = scene != DiceScene.MATCH || !stillMine
            val filled = occ[cat] == CellStatus.FILLED
            if (leftRound || filled) {
                used.add(cat)
                if (cat.isUpper) upperSum += points
                roundText = "${used.size}/8"
                log("  ✓ 已登记 ${cat.label}，本局进度 ${used.size}/8")
                publish()
                return true
            }
            log("  !! 登记未见效，重试")
        }
        log("  !! 登记连续失败，改由棋盘自检对齐状态")
        syncBoard("登记失败后对齐")
        return false
    }

    // ------------------------------------------------------------------ 棋盘自检

    /**
     * 以计分板和左上角 N/8 为准，同步「已登记集合」。
     * 每条保护规则都对应脚本里踩过的坑，注释见各自分支。
     */
    private suspend fun syncBoard(reason: String = "", info: FrameInfo? = null) {
        val det = if (info != null) {
            info.occupancy
        } else {
            val f = grab()
            val m = f?.info?.occupancy ?: emptyMap()
            f?.close()
            m
        }
        if (det.isEmpty()) return

        val raw = det.filterValues { it == CellStatus.FILLED }.keys.toMutableSet()
        val overlay = det.filterValues { it == CellStatus.OVERLAY }.keys.toList()
        val green = det.filterValues { it == CellStatus.GREEN }.keys.toList()
        if (overlay.isNotEmpty()) {
            log("  计分板: ${overlay.joinToString("、") { it.label }} 被提示面板遮挡，本帧跳过判定")
        }
        if (green.isNotEmpty()) {
            log("  计分板: ${green.joinToString("、") { it.label }} 是绿色背景数字，按规则忽略")
        }

        val rc = if (info != null) {
            if (info.round != null) info.round to (info.roundTotal ?: 8) else null
        } else null

        if (rc != null) {
            var n = rc.first
            val total = rc.second
            if (roundNum != null && n < roundNum!!) {
                log("  !! 回合读数 $n/$total 小于之前读到的 ${roundNum}，视为可疑，沿用 ${roundNum}")
                n = roundNum!!
            }
            roundNum = n
            roundText = "$n/$total"
            val exp = if (n == total && raw.size >= total) total else n - 1
            if (raw.size < exp && overlay.isNotEmpty() && raw.size + overlay.size >= exp) {
                val adopt = overlay.take(exp - raw.size)
                raw.addAll(adopt)
                log("  计分板: 被遮挡的 ${adopt.joinToString("、") { it.label }} 按已登记补齐")
            }
            if (raw.size > exp) {
                // 多的格子判为误读，丢掉最先读到的几个（脚本按墨迹量排序，
                // 这里没有逐格墨迹值，退化为按集合顺序，注释说明差别）
                val drop = raw.take(raw.size - exp)
                drop.forEach {
                    raw.remove(it)
                    log("  计分板: 回合进度 $roundText 显示应为 $exp 格，丢弃多余的『${it.label}』")
                }
            } else if (raw.size < exp) {
                log("  !! 计分板只读到 ${raw.size} 格，但回合进度 $roundText 显示应为 $exp 格")
            }
        } else {
            roundText = "-"
        }

        for (c in raw) missingCount.remove(c)
        val missing = used.filter { it !in raw }
        val dropped = mutableSetOf<DiceCategory>()
        for (c in missing) {
            val n = (missingCount[c] ?: 0) + 1
            missingCount[c] = n
            if (n >= OCC_MISS_TOLERANCE) dropped.add(c)
        }
        if (dropped.isNotEmpty()) {
            log("  棋盘自检: 连续 $OCC_MISS_TOLERANCE 次读不到 " +
                "${dropped.joinToString("、") { it.label }}，判定为内部误记并剔除")
            used.removeAll(dropped)
            for (c in dropped) {
                missingCount.remove(c)
                estPoints.remove(c)?.let { upperSum -= it }
            }
        } else if (missing.isNotEmpty()) {
            log("  棋盘自检: 本次读不到 ${missing.joinToString("、") { it.label }}，先按并集保留")
        }

        val extra = raw.filter { it !in used }
        if (extra.isNotEmpty()) {
            used.addAll(extra)
            val est = extra.filter { it.isUpper }
            if (est.isNotEmpty()) {
                upperSum += UNKNOWN_UPPER_ESTIMATE * est.size
                est.forEach { estPoints[it] = (estPoints[it] ?: 0) + UNKNOWN_UPPER_ESTIMATE }
            }
            log("  棋盘自检${if (reason.isEmpty()) "" else "[$reason]"}: 以计分板为准，发现额外登记 " +
                "${extra.joinToString("、") { it.label }}（疑似超时被自动登记）")
        }

        if (roundText == "-") {
            val est = maxOf(used.size, roundNum ?: 0)
            roundText = "$est/8?"
            log("  !! 左上角回合读数失败，进度按估算显示 $roundText")
        }
        publish()
    }

    // ------------------------------------------------------------------ 文案

    private fun fmtDie(d: DetectedDie): String = when {
        d.kind == DieKind.GREEN && d.value == null -> "绿万能"
        d.kind == DieKind.GREEN -> "绿${d.value}"
        else -> "橙${d.value}"
    }

    private fun sceneName(s: DiceScene): String = when (s) {
        DiceScene.MATCH -> "对局"
        DiceScene.MAIN -> "主界面"
        DiceScene.RESULT -> "结算"
        DiceScene.VICTORY -> "胜利"
        DiceScene.DIALOG -> "弹窗"
        DiceScene.UNKNOWN -> "未知"
    }
}
