package com.trickcal.crayon.master.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.capture.ImportOverlay
import com.trickcal.crayon.master.capture.ImportRunner
import com.trickcal.crayon.master.capture.ScreenCaptureService
import com.trickcal.crayon.master.capture.TapService
import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.Tc
import kotlinx.coroutines.launch

/** 识图导入：授权 → 选属性 → 自动识别 → 预览 → 写入。 */
@Composable
fun CrayonImportScreen(vm: AppViewModel, onBack: () -> Unit, onStatic: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var captureGranted by remember { mutableStateOf(ScreenCaptureService.instance != null) }
    var tapReady by remember { mutableStateOf(TapService.isReady()) }
    var runner by remember { mutableStateOf<ImportRunner?>(null) }
    var status by remember { mutableStateOf("尚未开始") }
    var running by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Map<String, Map<Attr, MutableSet<Int>>>?>(null) }

    val allAttrs = listOf(Attr.HEALTH, Attr.ATTACK, Attr.CRIT, Attr.CRIT_RESIST, Attr.DEFENSE)
    var selected by remember { mutableStateOf(allAttrs.toSet()) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(context, result.resultCode, data)
            captureGranted = true
        }
    }

    val overlay = remember { ImportOverlay(context) }
    DisposableEffect(Unit) {
        onDispose { overlay.hide() }
    }

    fun startImport() {
        if (running) return
        running = true
        preview = null
        status = "准备开始，请切到游戏涂色板指南"
        val names = vm.data.characters.map { it.name }
        val r = ImportRunner(context, names) { p ->
            status = p.error ?: p.pageText
            val lines = p.logs.ifEmpty { listOf(p.pageText) }
            overlay.updateLines(lines)
            if (p.finished) {
                running = false
                overlay.hide()
                ScreenCaptureService.stop(context)
                captureGranted = false
            }
        }
        runner = r
        // 悬浮窗先停在「待开始」，用户切到游戏后再手动点开始
        overlay.show(
            onStart = {
                status = "识别中…"
                scope.launch {
                    r.run(selected.toList())
                    // 收尾：把结果交给预览，否则不会出现「写入进度」入口
                    preview = r.result
                    status = "识别完成，共 ${r.result.size} 个角色命中"
                    running = false
                }
            },
            onStop = {
                r.cancel()
                overlay.update("正在停止…")
            },
        )
        overlay.updateLines(listOf("已就绪", "请切到游戏涂色板指南", "再点「开始」"))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            BackButton(label = "工具", onClick = onBack)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "蜡笔导入",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = Tc.colors.ink,
            )
            Text(
                text = "打开游戏的涂色板指南，程序会自动切属性页、滑动并读取打勾的格子。",
                fontSize = 11.sp,
                color = Tc.colors.ink3,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )

            // 静态识图入口
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onStatic() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("🖼️", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("静态识图", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                        Text(
                            text = "批量导入相册截图识别，不需要无障碍",
                            fontSize = 9.5.sp,
                            color = Tc.colors.ink3,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text("›", fontSize = 16.sp, color = Tc.colors.ink3)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 权限
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("权限准备", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Spacer(Modifier.height(8.dp))
                PermissionRow(
                    title = "屏幕截取",
                    desc = if (captureGranted) "已授权" else "需要授权一次，用于读取游戏画面",
                    ok = captureGranted,
                    action = "授权",
                ) {
                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    captureLauncher.launch(mgr.createScreenCaptureIntent())
                }
                Spacer(Modifier.height(6.dp))
                PermissionRow(
                    title = "无障碍服务",
                    desc = if (tapReady) "已开启" else "用于自动滑动与切换属性页",
                    ok = tapReady,
                    action = "去开启",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                PermissionRow(
                    title = "悬浮窗",
                    desc = if (overlayGranted) "已授权" else "识别时显示进度与停止按钮",
                    ok = overlayGranted,
                    action = "授权",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 属性选择
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("识别属性", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selected.size == allAttrs.size) "全属性" else "${selected.size} 项",
                        fontSize = 10.5.sp,
                        color = Tc.colors.brand,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (selected.size == allAttrs.size) "只选一项" else "全选",
                        fontSize = 10.5.sp,
                        color = Tc.colors.ink3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable {
                                selected = if (selected.size == allAttrs.size) {
                                    setOf(allAttrs.first())
                                } else {
                                    allAttrs.toSet()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    allAttrs.forEach { a ->
                        val on = selected.contains(a)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) Tc.colors.brand else Tc.colors.surface2)
                                .clickable {
                                    selected = if (on) selected - a else selected + a
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = a.label,
                                fontSize = 9.5.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) Color.White else Tc.colors.ink2,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 状态与结果
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("状态", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Text(
                    text = status,
                    fontSize = 11.sp,
                    color = Tc.colors.ink2,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                val p = preview
                if (p != null) {
                    Spacer(Modifier.height(10.dp))
                    val totalCells = p.values.sumOf { m -> m.values.sumOf { it.size } }
                    Text(
                        text = "命中 ${p.size} 个角色 · ${totalCells} 个属性层",
                        fontSize = 11.sp,
                        color = Tc.colors.brand,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Tc.colors.surface2)
                                .clickable { preview = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("放弃", fontSize = 11.5.sp, color = Tc.colors.ink2)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Tc.colors.brand)
                                .clickable {
                                    val (cells, chars) = vm.applyImportedCells(p)
                                    status = "已写入 $chars 个角色、$cells 个格子"
                                    preview = null
                                    vm.showToast("导入完成：$cells 格")
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("写入进度", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // 底部按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Tc.colors.paper)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            val ready = captureGranted && tapReady && overlayGranted && selected.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (running) Tc.colors.warn else if (ready) Tc.colors.brand else Tc.colors.ink.copy(alpha = 0.10f))
                    .clickable {
                        if (running) {
                            runner?.cancel()
                            overlay.update("正在停止…")
                        } else if (ready) {
                            startImport()
                        } else {
                            vm.showToast("请先完成权限授权并选择属性")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (running) "停止识别" else "开始识别",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running || ready) Color.White else Tc.colors.ink3,
                )
            }
        }
    }

    // 权限状态轮询（无障碍服务连接时机不确定，且从系统设置返回后需要自动刷新）
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(context)
            captureGranted = ScreenCaptureService.instance != null
            tapReady = TapService.isReady()
            kotlinx.coroutines.delay(800)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tc.colors.ink)
            Text(desc, fontSize = 9.5.sp, color = if (ok) Tc.colors.ok else Tc.colors.ink3)
        }
        if (!ok) {
            Text(
                text = action,
                fontSize = 11.sp,
                color = Tc.colors.brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tc.colors.brandSoft)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        } else {
            Text("✓", fontSize = 13.sp, color = Tc.colors.ok)
        }
    }
}
