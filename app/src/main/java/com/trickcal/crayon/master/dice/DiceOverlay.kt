package com.trickcal.crayon.master.dice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 挂机状态悬浮窗。
 *
 * 位置固定在右下角：MediaProjection 会把悬浮窗一起截进去，落在识别区就会污染读数。
 * 基准坐标下，骰区是 x 640~1035 / y 185~580，计分板是 x 23~446 / y 122~685，
 * 左上角还有 N/8 计数器 —— 只有右下角（x>1080, y>600）不与任何识别区或采样点相交。
 *
 * 用原生 View 而不是 Compose，避免跨窗口缺 LifecycleOwner 的问题（与 ImportOverlay 同因）。
 */
class DiceOverlay(private val context: Context) {

    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var status: TextView? = null
    private var pauseBtn: TextView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    var onPauseToggle: ((Boolean) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    private var paused = false

    @SuppressLint("ClickableViewAccessibility")
    fun show(): Boolean {
        if (root != null) return true
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E61C1916"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(context).apply {
            text = "诡计骰子"
            setTextColor(Color.parseColor("#FFF3E9"))
            textSize = 11.5f
            setPadding(0, 0, dp(6), 0)
        }
        title = titleView
        head.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(chip("暂停", "#E8A05F") {
            paused = !paused
            refreshPause()
            onPauseToggle?.invoke(paused)
        }.also { pauseBtn = it })
        head.addView(chip("停止", "#D8654F") { onStop?.invoke() })
        container.addView(head)

        val statusView = TextView(context).apply {
            setTextColor(Color.parseColor("#D9CFC4"))
            textSize = 10f
            maxLines = 6
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(6), 0, 0)
            text = "等待启动"
        }
        status = statusView
        container.addView(statusView)

        val lp = WindowManager.LayoutParams(
            dp(232),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(8)
            y = dp(8)
        }
        params = lp

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        container.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    manager.updateViewLayout(container, lp)
                    true
                }

                else -> false
            }
        }

        // 没有悬浮窗权限时 addView 会抛 BadTokenException，不能让它把进程带崩
        val ok = runCatching { manager.addView(container, lp) }.isSuccess
        root = if (ok) container else null
        return ok
    }

    fun update(s: DiceState) {
        title?.post {
            title?.text = "诡计骰子 ${s.played}/${s.maxGames}"
        }
        val anchor = if (s.anchorOk) "" else " · 锚点未检出"
        val lines = buildString {
            append("${s.scene} · 回合 ${s.round} · 窗口 ${s.window}$anchor")
            append("\n骰 ${s.dice}")
            append("\n已登记 ${s.used}")
            if (s.logs.isNotEmpty()) {
                append("\n")
                append(s.logs.takeLast(2).joinToString("\n"))
            }
        }
        status?.post { status?.text = lines }
        refreshPause()
    }

    private fun refreshPause() {
        pauseBtn?.post {
            pauseBtn?.text = if (paused) "继续" else "暂停"
            pauseBtn?.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(Color.parseColor(if (paused) "#7FB069" else "#E8A05F"))
            }
        }
    }

    private fun chip(text: String, color: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            textSize = 10.5f
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(Color.parseColor(color))
            }
            setOnClickListener { onClick() }
        }

    fun hide() {
        root?.let { runCatching { wm?.removeView(it) } }
        root = null
        title = null
        status = null
        pauseBtn = null
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
