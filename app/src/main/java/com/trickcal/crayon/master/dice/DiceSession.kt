package com.trickcal.crayon.master.dice

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.trickcal.crayon.master.capture.ScreenCaptureService
import com.trickcal.crayon.master.capture.TapService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 挂机会话的持有者。
 *
 * 为什么不放在 Composable 或 ViewModel 里：挂机时 App 全程在后台，
 * 界面随时可能被销毁重组，而 Runner 必须继续跑。所以协程挂在进程级的
 * SupervisorJob 上，只有显式 stop 或进程结束才会停。
 */
object DiceSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(DiceState())
    val state: StateFlow<DiceState> = _state

    private var job: Job? = null
    private var engine: DiceRunner? = null
    private var policy: DicePolicy? = null

    val running: Boolean get() = job?.isActive == true

    /**
     * 启动挂机。返回 null 表示已启动，否则是失败原因（直接展示给用户）。
     * 策略表首次加载需要解压 1.5MB，放在 IO 线程。
     */
    suspend fun start(context: Context, settings: DiceSettings): String? {
        if (running) return null
        if (ScreenCaptureService.instance == null) return "截屏服务未启动，请先授权屏幕截取"
        if (!TapService.isReady()) return "无障碍服务未开启，无法注入点击"

        val p = try {
            policy ?: withContext(Dispatchers.IO) {
                context.applicationContext.assets.open("dice/tables.bin").use {
                    DicePolicy.fromStream(it)
                }
            }.also { policy = it }
        } catch (t: Throwable) {
            return "策略表加载失败：${t.message}"
        }

        // 数字模板缺失不致命：N/8 读不到时会退回「计分板格数 + ?」估算
        DiceVision.init(context.applicationContext)

        val runner = DiceRunner(AndroidHost(), p, settings)
        engine = runner
        _state.value = DiceState(maxGames = settings.maxGames, played = settings.startPlayed, running = true)
        job = scope.launch {
            launch { runner.state.collect { _state.value = it } }
            try {
                runner.run()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                val msg = "挂机异常: ${t.message ?: t::class.java.simpleName}"
                _state.value = _state.value.copy(
                    logs = (_state.value.logs + msg).takeLast(6),
                )
            } finally {
                _state.value = _state.value.copy(running = false, paused = false)
                engine = null
                job = null
            }
        }
        return null
    }

    fun stop() {
        engine?.pauseRequested = false
        job?.cancel()
        job = null
        engine = null
        _state.value = _state.value.copy(running = false, paused = false)
    }

    fun setPaused(paused: Boolean) {
        engine?.pauseRequested = paused
        _state.value = _state.value.copy(paused = paused)
    }

    private class AndroidHost : DiceHost {
        override fun snapshot(): Bitmap? = ScreenCaptureService.instance?.snapshot()
        override fun tap(x: Float, y: Float): Boolean = TapService.tap(x, y)
        override fun nowMs(): Long = SystemClock.elapsedRealtime()
    }
}
