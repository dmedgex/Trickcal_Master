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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trickcal.crayon.master.capture.ScreenCaptureService
import com.trickcal.crayon.master.capture.TapService
import com.trickcal.crayon.master.dice.DiceOverlay
import com.trickcal.crayon.master.dice.DiceSession
import com.trickcal.crayon.master.dice.DiceSettings
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.brandGradient
import com.trickcal.crayon.master.ui.theme.Tc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 诡计骰子挂机页。
 *
 * 与识图导入的区别：导入是「一次性跑完」，挂机是「长时间后台运行」，
 * 所以状态从 [DiceSession] 的 StateFlow 读，页面销毁不影响 Runner。
 */
@Composable
fun DiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by DiceSession.state.collectAsStateWithLifecycle()

    var captureOk by remember { mutableStateOf(ScreenCaptureService.instance != null) }
    var tapOk by remember { mutableStateOf(TapService.isReady()) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var maxGames by remember { mutableIntStateOf(80) }
    var startPlayed by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val overlay = remember { DiceOverlay(context) }
    overlay.onStop = { DiceSession.stop() }
    overlay.onPauseToggle = { DiceSession.setPaused(it) }

    // 授权状态从系统读，用户可能去设置页开了又回来
    LaunchedEffect(Unit) {
        while (true) {
            captureOk = ScreenCaptureService.instance != null
            tapOk = TapService.isReady()
            overlayOk = Settings.canDrawOverlays(context)
            delay(800)
        }
    }

    LaunchedEffect(state) {
        if (state.running) overlay.update(state) else overlay.hide()
    }
    DisposableEffect(Unit) {
        onDispose { overlay.hide() }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(context, result.resultCode, data)
            captureOk = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            BackButton(label = "工具", onClick = onBack)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "诡计骰子",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = Tc.colors.ink,
            )
            Text(
                text = "读取游戏画面并按最优策略自动投掷、保留、登记。" +
                    "启动后切到游戏即可，悬浮窗会显示当前进度。",
                fontSize = 11.sp,
                color = Tc.colors.ink3,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )

            // ---- 权限 ----
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
                    desc = if (captureOk) "已授权" else "读取游戏画面，每次启动需重新授权",
                    ok = captureOk,
                    action = "授权",
                ) {
                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    captureLauncher.launch(mgr.createScreenCaptureIntent())
                }
                Spacer(Modifier.height(6.dp))
                PermissionRow(
                    title = "无障碍服务",
                    desc = if (tapOk) "已开启" else "注入点击手势，需要手动在系统设置里开启",
                    ok = tapOk,
                    action = "去开启",
                ) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
                Spacer(Modifier.height(6.dp))
                PermissionRow(
                    title = "悬浮窗",
                    desc = if (overlayOk) "已授权" else "挂机时显示进度与停止按钮",
                    ok = overlayOk,
                    action = "去开启",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 参数 ----
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("挂机参数", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Spacer(Modifier.height(4.dp))
                NumberRow(
                    label = "今日已玩",
                    hint = "启动时已用掉的入场次数",
                    value = startPlayed,
                    onChange = { startPlayed = it.coerceIn(0, maxGames) },
                )
                NumberRow(
                    label = "目标局数",
                    hint = "脚本默认 80，按每日入场上限填",
                    value = maxGames,
                    onChange = { maxGames = it.coerceIn(1, 200) },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 启动 ----
            val ready = captureOk && tapOk && overlayOk && !state.running
            val startBg = if (state.running) {
                androidx.compose.ui.graphics.SolidColor(Tc.colors.line)
            } else {
                brandGradient()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(startBg)
                    .clickable(enabled = !busy) {
                        if (state.running) {
                            DiceSession.stop()
                            overlay.hide()
                        } else {
                            if (!captureOk || !tapOk || !overlayOk) {
                                error = "请先完成三项授权"
                                return@clickable
                            }
                            busy = true
                            error = null
                            scope.launch {
                                val err = DiceSession.start(
                                    context,
                                    DiceSettings(maxGames = maxGames, startPlayed = startPlayed),
                                )
                                busy = false
                                if (err != null) {
                                    error = err
                                } else if (!overlay.show()) {
                                    error = "悬浮窗创建失败，请检查悬浮窗权限"
                                    DiceSession.stop()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        busy -> "启动中…"
                        state.running -> "停止挂机"
                        !ready -> "开始挂机（缺授权）"
                        else -> "开始挂机"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.running) Tc.colors.ink2 else Color.White,
                )
            }

            error?.let {
                Text(
                    text = it,
                    fontSize = 10.5.sp,
                    color = Tc.colors.warn,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.running) {
                Spacer(Modifier.height(14.dp))
                TcCard(
                    padding = 14.dp,
                    shape = RoundedCornerShape(18.dp),
                    elevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "运行状态" + if (state.paused) " · 已暂停" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tc.colors.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusLine("场景", state.scene)
                    StatusLine("回合", state.round)
                    StatusLine("局数", "${state.played} / ${state.maxGames}")
                    StatusLine("窗口", state.window)
                    StatusLine("骰子", state.dice)
                    StatusLine("已登记", state.used)
                    StatusLine("上级总和", state.upper)
                    if (!state.anchorOk) {
                        Text(
                            text = "计分板锚点未检出，当前按 1280×720 恒等映射运行",
                            fontSize = 10.sp,
                            color = Tc.colors.warn,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (state.logs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.logs.forEach {
                            Text(
                                text = it,
                                fontSize = 10.sp,
                                color = Tc.colors.ink3,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "自动化操作可能违反游戏用户协议，存在账号处罚风险，请自行判断是否使用。",
                fontSize = 10.sp,
                color = Tc.colors.ink3,
                lineHeight = 15.sp,
            )
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
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Tc.colors.ok else Tc.colors.warn),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Tc.colors.ink)
            Text(desc, fontSize = 9.5.sp, color = Tc.colors.ink3, lineHeight = 14.sp)
        }
        if (!ok) {
            Text(
                text = action,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tc.colors.brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onClick() }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun NumberRow(
    label: String,
    hint: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Tc.colors.ink)
            Text(hint, fontSize = 9.5.sp, color = Tc.colors.ink3)
        }
        StepBtn("−") { onChange(value - 1) }
        Text(
            text = value.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Tc.colors.ink,
            modifier = Modifier
                .width(44.dp)
                .padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepBtn("+") { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tc.colors.surface2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink2)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 10.5.sp, color = Tc.colors.ink3)
        Text(value, fontSize = 10.5.sp, color = Tc.colors.ink, fontWeight = FontWeight.Medium)
    }
}
