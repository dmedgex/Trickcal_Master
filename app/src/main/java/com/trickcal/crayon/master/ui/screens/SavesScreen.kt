package com.trickcal.crayon.master.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.TagPill
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.Tc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val stats = remember(vm.lit) { BoardEngine.globalStats(vm.data, vm.lit) }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<DangerAction?>(null) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(vm.exportJson().toByteArray(Charsets.UTF_8))
            }
        }.isSuccess
        vm.showToast(if (ok) "已导出存档" else "导出失败")
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        vm.showToast(
            when {
                text == null -> "无法读取文件"
                vm.importJson(text) -> "导入成功"
                else -> "格式无法识别"
            },
        )
    }

    PageColumn {
        Spacer(Modifier.height(10.dp))
        BackButton(label = "我的", onClick = onBack)
        Spacer(Modifier.height(12.dp))
        Text("存档中心", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
        Spacer(Modifier.height(12.dp))

        TcCard(padding = 16.dp, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前存档", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Spacer(Modifier.weight(1f))
                TagPill("本地", color = Tc.colors.ok, background = Tc.colors.ok.copy(alpha = 0.14f))
            }
            Spacer(Modifier.height(10.dp))
            KvRow("涂色进度", "${stats.lit} / ${stats.total} 格")
            KvRow("已用金蜡笔", "${stats.gold} 根")
            KvRow("派遣选择", "${vm.selectedPets.size} 只")
            KvRow("任务特性自定义", "${vm.overrides.size} 条")
            KvRow("自动快照", "${vm.backups.size} 份")
            KvRow("最后备份", timeAgo(vm.lastBackup), last = true)
        }

        Spacer(Modifier.height(12.dp))
        Text("导出", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "导出存档",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tc.colors.brand)
                    .clickable {
                        exporter.launch("trickcal-save-${System.currentTimeMillis()}.json")
                    }
                    .padding(vertical = 11.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "创建快照",
                fontSize = 11.5.sp,
                color = Tc.colors.ink2,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tc.colors.surface2)
                    .clickable { vm.createBackup() }
                    .padding(vertical = 11.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "导出为 JSON，含涂色进度与派遣配置；导入时自动校验格子 ID。",
            fontSize = 9.5.sp,
            color = Tc.colors.ink3,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(14.dp))
        Text("导入", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        Spacer(Modifier.height(8.dp))
        TcCard(
            padding = 13.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📥", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("从文件导入", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tc.colors.ink)
                    Text("兼容新格式与旧版 litSlotIds", fontSize = 9.5.sp, color = Tc.colors.ink3)
                }
                Text("›", fontSize = 16.sp, color = Tc.colors.brand)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动快照", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
            Spacer(Modifier.weight(1f))
            Text("保留最近 10 份", fontSize = 9.5.sp, color = Tc.colors.ink3)
        }
        Spacer(Modifier.height(8.dp))

        if (vm.backups.isEmpty()) {
            TcCard(padding = 14.dp, modifier = Modifier.fillMaxWidth()) {
                Text("还没有快照，点「创建快照」先存一份。", fontSize = 11.sp, color = Tc.colors.ink3)
            }
        } else {
            vm.backups.forEach { entry ->
                TcCard(
                    padding = 13.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.restoreBackup(entry) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🕐", fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(formatTime(entry.at), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("${entry.litCount} 格 · 自动", fontSize = 9.5.sp, color = Tc.colors.ink3)
                        }
                        Text("回滚", fontSize = 10.5.sp, color = Tc.colors.brand)
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("危险操作", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "载入示例进度",
                fontSize = 11.sp,
                color = Tc.colors.ink2,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tc.colors.surface2)
                    .clickable { pending = DangerAction.LOAD_DEMO }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "清空涂色进度",
                fontSize = 11.sp,
                color = Tc.colors.warn,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tc.colors.warn.copy(alpha = 0.12f))
                    .clickable { pending = DangerAction.CLEAR }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    val action = pending
    if (action != null) {
        DangerConfirmDialog(
            action = action,
            litCount = stats.lit,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                when (action) {
                    DangerAction.LOAD_DEMO -> vm.loadDemoProgress()
                    DangerAction.CLEAR -> vm.clearProgress()
                }
            },
        )
    }
}

/** 危险操作二次确认：明确标注这是调试功能。 */
private enum class DangerAction { LOAD_DEMO, CLEAR }

@Composable
private fun DangerConfirmDialog(
    action: DangerAction,
    litCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val loadDemo = action == DangerAction.LOAD_DEMO
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tc.colors.surface,
        title = {
            Text(
                text = if (loadDemo) "载入示例进度" else "清空涂色进度",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Tc.colors.ink,
            )
        },
        text = {
            Column {
                TagPill(
                    text = "调试功能",
                    color = Tc.colors.warn,
                    background = Tc.colors.warn.copy(alpha = 0.14f),
                )
                Text(
                    text = if (loadDemo) {
                        "仅用于演示与自测，不是正式功能。载入后当前涂色进度会立即被示例数据覆盖，无法撤销。"
                    } else {
                        "仅用于演示与自测，不是正式功能。清空后当前全部涂色进度都会丢失，无法撤销。"
                    },
                    fontSize = 11.5.sp,
                    color = Tc.colors.ink2,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "当前进度 $litCount 格，建议先在上方「创建快照」留一份备份。",
                    fontSize = 10.5.sp,
                    color = Tc.colors.ink3,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = if (loadDemo) "仍要载入" else "仍要清空",
                    color = if (loadDemo) Tc.colors.brand else Tc.colors.warn,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Tc.colors.ink3)
            }
        },
    )
}

@Composable
private fun KvRow(key: String, value: String, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(key, fontSize = 12.sp, color = Tc.colors.ink2, modifier = Modifier.weight(1f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tc.colors.line))
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))
