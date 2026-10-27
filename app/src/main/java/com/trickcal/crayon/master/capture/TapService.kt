package com.trickcal.crayon.master.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：只做手势注入（点击 / 滑动），不读取窗口内容。
 * 悬浮窗按钮和自动流程都通过它操作游戏界面。
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

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
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
        val movePath = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val moveStroke = GestureDescription.StrokeDescription(movePath, 0, moveMs, true)
        val first = GestureDescription.Builder().addStroke(moveStroke).build()
        return dispatchGesture(
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
                    }.onFailure {
                        onDone()
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onDone()
                }
            },
            null,
        )
    }

    fun swipeFrom(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
