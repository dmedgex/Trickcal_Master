package com.trickcal.crayon.master.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 屏幕采集服务：持有 MediaProjection，持续把最新一帧存下来供识别读取。
 * Android 9 没有 AccessibilityService#takeScreenshot，只能走这条路径。
 */
class ScreenCaptureService : Service() {

    companion object {
        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        private const val CHANNEL_ID = "capture"
        private const val NOTI_ID = 4101

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("code", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var latest: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTI_ID, buildNotification())
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null || projection != null) {
            return START_NOT_STICKY
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mgr.getMediaProjection(code, data)
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }, handler)

        // 必须用「物理屏幕的横屏尺寸」，不能用 resources.displayMetrics：
        // 后者跟随 app 自身方向（本 app 竖屏），而游戏是横屏，会让游戏画面被压进竖屏画布
        // （实测截出来是 405x720，上下大片留白，卡片检测直接 0 张）。
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealMetrics(dm)
        val w = maxOf(dm.widthPixels, dm.heightPixels)
        val h = minOf(dm.widthPixels, dm.heightPixels)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val rowStride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val rowPadding = rowStride - pixelStride * w
                    val bmpW = w + rowPadding / pixelStride
                    val bmp = Bitmap.createBitmap(bmpW, h, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(plane.buffer)
                    val cropped = if (bmpW != w) {
                        Bitmap.createBitmap(bmp, 0, 0, w, h).also { bmp.recycle() }
                    } else {
                        bmp
                    }
                    latest?.recycle()
                    latest = cropped
                } catch (_: Throwable) {
                    // 忽略单帧失败
                } finally {
                    image.close()
                }
            }, handler)
        }
        display = p.createVirtualDisplay(
            "capture",
            w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler,
        )
        instance = this
        return START_STICKY
    }

    /** 取当前屏幕帧的副本；没有帧时返回 null。 */
    fun snapshot(): Bitmap? = latest?.let {
        if (it.isRecycled) null else it.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "识图导入", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("识图导入运行中")
            .setContentText("正在读取游戏画面，返回应用可停止")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun release() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        latest?.recycle()
        latest = null
    }

    override fun onDestroy() {
        instance = null
        release()
        super.onDestroy()
    }
}
