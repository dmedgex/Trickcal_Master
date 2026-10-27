package com.trickcal.crayon.master.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 识别过程中的悬浮窗：显示当前进度，随时可以中止。
 * 用原生 View 而不是 Compose，避免跨窗口的 LifecycleOwner 问题。
 */
class ImportOverlay(private val context: Context) {

    private var root: LinearLayout? = null
    private var label: TextView? = null
    private var actionBtn: TextView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var running = false
    private var onStart: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(onStart: () -> Unit, onStop: () -> Unit) {
        this.onStart = onStart
        this.onStop = onStop
        running = false
        if (root != null) { refreshButtons(); return }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F2FFFFFF"))
                setStroke(dp(1), Color.parseColor("#22000000"))
            }
        }

        val labelView = TextView(context).apply {
            setText("已就绪")
            setTextColor(Color.parseColor("#2A2320"))
            textSize = 11f
            maxLines = 4
            setLineSpacing(0f, 1.15f)
        }
        label = labelView
        container.addView(
            labelView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val action = TextView(context).apply {
            setText(if (running) "停止" else "开始")
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(if (running) "#E8845F" else "#8CC63F"))
            }
            setOnClickListener {
                if (running) {
                    running = false
                    refreshButtons()
                    onStop?.invoke()
                } else {
                    running = true
                    refreshButtons()
                    onStart?.invoke()
                }
            }
        }
        actionBtn = action
        container.addView(action)
        refreshButtons()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(8)
        }
        params = lp

        // 拖动
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

        manager.addView(container, lp)
        root = container
    }

    private fun actionButton(text: String, color: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
            }
            setOnClickListener { onClick() }
        }

    private fun refreshButtons() {
        actionBtn?.post {
            actionBtn?.text = if (running) "停止" else "开始"
            actionBtn?.isEnabled = true
            actionBtn?.alpha = 1f
            actionBtn?.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(if (running) "#E8845F" else "#8CC63F"))
            }
        }
    }

    fun update(message: String) {
        label?.post { label?.text = message }
    }

    /** 显示最近几行日志（新的在下）。 */
    fun updateLines(lines: List<String>) {
        if (lines.isEmpty()) return
        label?.post { label?.text = lines.joinToString("\n") }
    }

    fun hide() {
        root?.let { runCatching { wm?.removeView(it) } }
        root = null
        label = null
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
