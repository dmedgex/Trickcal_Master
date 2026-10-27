package com.trickcal.crayon.master.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.ThemeMode
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.AppSkin
import com.trickcal.crayon.master.ui.theme.Tc

private data class MeItem(
    val emoji: String,
    val title: String,
    val desc: String,
    val avatarPath: String? = null,
    val action: () -> Unit,
)

@Composable
fun MeScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val stats = remember(vm.lit) { BoardEngine.globalStats(vm.data, vm.lit) }
    var showTheme by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    PageColumn {
        Spacer(Modifier.height(14.dp))
        Text("账户", fontSize = 13.sp, letterSpacing = 0.3.sp, color = Tc.colors.ink3)
        Spacer(Modifier.height(5.dp))
        Text(
            text = "我的",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            lineHeight = 32.sp,
            color = Tc.colors.ink,
        )
        Spacer(Modifier.height(18.dp))

        // .savecard
        TcCard(
            padding = 17.dp,
            shape = RoundedCornerShape(28.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("☁️ 存档中心", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
            Text(
                text = "当前进度：${stats.lit}/${stats.total} 格 · 上次备份 ${timeAgo(vm.lastBackup)}",
                fontSize = 11.sp,
                color = Tc.colors.ink2,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 13.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "管理存档",
                    fontSize = 11.5.sp,
                    color = Tc.colors.ink2,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tc.colors.surface2)
                        .clickable { onNavigate("saves") }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "立即备份",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tc.colors.brand)
                        .clickable { vm.createBackup() }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        MeList(
            items = listOf(
                MeItem("💾", "导出与导入", "新存档格式 · 兼容旧版 JSON") { onNavigate("saves") },
                MeItem("🎨", "外观", "${vm.skin.label} · ${vm.themeMode.label}") { showTheme = true },
            ),
        )

        MeList(
            items = listOf(
                MeItem("🔄", "检查更新", "当前 v${AppViewModel.VERSION_NAME}") { vm.showToast("已是最新版本") },
                MeItem("ℹ️", "关于", "作者 梦之边缘X · 版权声明") { showAbout = true },
            ),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "全部数据来自本机解析，不上传任何信息。",
            fontSize = 9.5.sp,
            color = Tc.colors.ink3,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    if (showTheme) {
        ThemeDialog(vm) { showTheme = false }
    }
    if (showAbout) {
        AboutDialog { showAbout = false }
    }
}

/** 关于：弹出作者与版权声明（不放头像）。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tc.colors.surface,
        title = {
            Text("关于", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        },
        text = {
            Column {
                Text(
                    text = "圣团长日记",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    color = Tc.colors.ink,
                )
                Text(
                    text = "v${AppViewModel.VERSION_NAME} · 第三方非官方工具",
                    fontSize = 10.5.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier.padding(top = 3.dp),
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 11.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Tc.colors.line),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("作者", fontSize = 12.sp, color = Tc.colors.ink2, modifier = Modifier.weight(1f))
                    Text(
                        text = "B站 @梦之边缘X",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tc.colors.ink,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("版权声明", fontSize = 10.5.sp, color = Tc.colors.ink3)
                Text(
                    text = "本工具为个人制作的离线辅助工具，与游戏官方无任何关联。" +
                        "《嘟嘟脸恶作剧》的游戏名称、角色形象、美术素材等内容的版权均归官方所有，" +
                        "本工具仅调用本机数据做整理与计算，不分发任何游戏资源。",
                    fontSize = 11.sp,
                    color = Tc.colors.ink2,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Tc.colors.brand)
            }
        },
    )
}

@Composable
private fun ThemeDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tc.colors.surface,
        title = {
            Text("外观", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("深浅模式", fontSize = 10.5.sp, color = Tc.colors.ink3)
                ThemeMode.entries.forEach { mode ->
                    val on = vm.themeMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Tc.colors.brandSoft else Tc.colors.surface2)
                            .clickable {
                                vm.changeThemeMode(mode)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Tc.colors.brand else Tc.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) {
                            Text("✓", fontSize = 13.sp, color = Tc.colors.brand)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("主题配色", fontSize = 10.5.sp, color = Tc.colors.ink3)
                AppSkin.entries.forEach { skin ->
                    val on = vm.skin == skin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Tc.colors.brandSoft else Tc.colors.surface2)
                            .clickable {
                                vm.changeSkin(skin)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(skin.swatch),
                        )
                        Text(
                            text = skin.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Tc.colors.brand else Tc.colors.ink,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        if (on) {
                            Text("✓", fontSize = 13.sp, color = Tc.colors.brand)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Tc.colors.ink3)
            }
        },
    )
}

/** .melist：圆角 22，行间 1px 分隔线，图标 30dp。 */
@Composable
private fun MeList(items: List<MeItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Tc.colors.surface)
            .padding(horizontal = 14.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Tc.colors.line),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.action)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Tc.colors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.avatarPath != null) {
                        AssetImage(
                            path = item.avatarPath,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(item.emoji, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tc.colors.ink,
                    )
                    Text(
                        text = item.desc,
                        fontSize = 10.5.sp,
                        color = Tc.colors.ink3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text("›", fontSize = 16.sp, color = Tc.colors.ink3)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}
