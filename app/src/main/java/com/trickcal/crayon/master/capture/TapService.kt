package com.trickcal.crayon.master.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：只做手势注入（点击 / 滑动），不读取窗口内容。
 *
 * 关键设计：**所有手势调用都必须在 try/catch 内**。
 * 坐标非法（NaN、负数、越界）会让 GestureDescription.Builder 抛异常，
 * 未捕获会直接让服务进程崩溃 —— 系统随后把服务标记为「此服务出现故障」并禁用，
 * 此后所有点击/滑动全部失效（真机实测过一次）。
 */
class TapService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TapService? = null
            private set

        fun isReady(): Boolean = instance != null

        fun tap(x: Float, y: Float): Boolean = instance?.tapAt(x, y) ?: false

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean =
            instance?.swipeFrom(x1, y1, x2, y2, duration) ?: false

        /**
         * 滑到位后继续按住 [holdMs] 再抬手，用来彻底消除 fling 惯性。
         * 无障碍手势不能"原地停留"，所以用 continueStroke 接一段极小时长位移的路径。
         */
        fun swipeHold(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            moveMs: Long,
            holdMs: Long,
            onDone: () -> Unit,
        ): Boolean = instance?.swipeHoldFrom(x1, y1, x2, y2, moveMs, holdMs, onDone) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 坐标合法性：有限值且非负 */
    private fun valid(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x >= 0f && y >= 0f

    fun tapAt(x: Float, y: Float): Boolean {
        if (!valid(x, y)) return false
        return runCatching {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            dispatchGesture(gesture, null, null)
        }.getOrDefault(false)
    }

    fun swipeFrom(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        if (!valid(x1, y1) || !valid(x2, y2)) return false
        return runCatching {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            dispatchGesture(gesture, null, null)
        }.getOrDefault(false)
    }

    fun swipeHoldFrom(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        moveMs: Long,
        holdMs: Long,
        onDone: () -> Unit,
    ): Boolean {
        if (!valid(x1, y1) || !valid(x2, y2)) {
            onDone()
            return false
        }
        return runCatching {
            val movePath = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val moveStroke = GestureDescription.StrokeDescription(movePath, 0, moveMs, true)
            val first = GestureDescription.Builder().addStroke(moveStroke).build()
            dispatchGesture(
                first,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        // 接着在同一笔触摸上再走 0.5px / holdMs，等于按住不动
                        val holdPath = Path().apply {
                            moveTo(x2, y2)
                            lineTo(x2, y2 + 0.5f)
                        }
                        runCatching {
                            val holdStroke = moveStroke.continueStroke(holdPath, 0, holdMs, false)
                            val second = GestureDescription.Builder().addStroke(holdStroke).build()
                            dispatchGesture(
                                second,
                                object : GestureResultCallback() {
                                    override fun onCompleted(g: GestureDescription?) = onDone()
                                    override fun onCancelled(g: GestureDescription?) = onDone()
                                },
                                null,
                            )
                        }.onFailure { onDone() }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone()
                    }
                },
                null,
            )
        }.onFailure { onDone() }.getOrDefault(false)
    }
}
