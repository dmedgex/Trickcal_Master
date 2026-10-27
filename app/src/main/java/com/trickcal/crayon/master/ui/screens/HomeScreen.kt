package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.FuncGlyph
import com.trickcal.crayon.master.ui.components.FuncGlyphIcon
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.ProgressRing
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.brandGradient
import com.trickcal.crayon.master.ui.theme.Tc
import java.util.Calendar
import kotlin.math.roundToInt

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "夜深了"
        h < 11 -> "早上好"
        h < 14 -> "中午好"
        h < 18 -> "下午好"
        else -> "晚上好"
    }
}

fun timeAgo(ts: Long): String {
    if (ts <= 0L) return "尚未备份"
    val m = (System.currentTimeMillis() - ts) / 60000
    if (m < 1) return "刚刚"
    if (m < 60) return "$m 分钟前"
    val h = m / 60
    if (h < 24) return "$h 小时前"
    return "${h / 24} 天前"
}

@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val stats = remember(vm.lit) { BoardEngine.globalStats(vm.data, vm.lit) }
    val pct = (stats.pct * 100).roundToInt()
    var showManage by remember { mutableStateOf(false) }

    PageColumn(horizontalPadding = 20.dp) {
        // .hero{padding:14px 0 18px}
        Spacer(Modifier.height(14.dp))
        Text(
            text = greeting() + "，圣团长",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.3.sp,
            color = Tc.colors.ink3,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = buildAnnotatedString {
                append("今天刷")
                withStyle(SpanStyle(color = Tc.colors.brand)) { append("金蜡笔") }
                append("了吗？")
            },
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            lineHeight = 32.sp,
            color = Tc.colors.ink,
        )
        Spacer(Modifier.height(18.dp))

        RingCard(stats.lit, stats.total, pct, stats.bonus, stats.gold, vm.data.characters.size)

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "全部功能",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = Tc.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "管理",
                fontSize = 12.sp,
                color = Tc.colors.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showManage = true }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        val ordered = vm.resolvedFeatureOrder(HOME_FEATURES.map { it.id })
            .mapNotNull { id -> HOME_FEATURES.firstOrNull { it.id == id } }
            .filterNot { vm.hiddenFeatures.contains(it.id) }
        val readyFeatures = ordered.filter { it.route != null }
        val soonFeatures = ordered.filter { it.route == null }

        FeatureGrid(vm, readyFeatures, onNavigate)

        if (soonFeatures.isNotEmpty()) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = "即将上线",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = Tc.colors.ink,
            )
            Spacer(Modifier.height(12.dp))
            FeatureGrid(vm, soonFeatures, onNavigate)
        }

        Spacer(Modifier.height(15.dp))

        Spacer(Modifier.height(26.dp))
        TcCard(
            padding = 14.dp,
            shape = RoundedCornerShape(22.dp),
            elevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onNavigate("saves") },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(pastel(0xFFE7F4EC)),
                    contentAlignment = Alignment.Center,
                ) { Text("☁️", fontSize = 17.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "上次备份 · ${timeAgo(vm.lastBackup)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tc.colors.ink,
                    )
                    Text(
                        text = "共 ${vm.backups.size} 个自动快照",
                        fontSize = 9.5.sp,
                        color = Tc.colors.ink3,
                    )
                }
                Text("›", fontSize = 16.sp, color = Tc.colors.brand)
            }
        }
    }

    if (showManage) {
        FeatureManageDialog(vm) { showManage = false }
    }
}

/** 对齐 `.ringcard`：150deg 品牌渐变 + 右上角金色径向光晕。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RingCard(
    lit: Int,
    total: Int,
    pct: Int,
    bonus: Int,
    gold: Int,
    characters: Int,
) {
    val shape = RoundedCornerShape(28.dp)
    val glowAlpha = if (Tc.colors.isDark) 0.30f else 0.26f
    // 光晕跟随皮肤主色，不再固定暖金
    val glowColor = Tc.colors.brand.copy(alpha = glowAlpha)
    val gradient = brandGradient()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(gradient)
            .drawBehind {
                val radius = 75.dp.toPx()
                val center = Offset(size.width - 40.dp.toPx() + radius * 0.45f, -40.dp.toPx() + radius * 0.45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = pct / 100f,
                    modifier = Modifier.size(92.dp),
                    strokeWidth = 8.dp,
                )
                Row {
                    Text(
                        text = pct.toString(),
                        modifier = Modifier.alignByBaseline(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = Tc.colors.ink,
                    )
                    Text(
                        text = "%",
                        modifier = Modifier.alignByBaseline().padding(start = 1.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = Tc.colors.ink3,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "涂色进度",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tc.colors.ink,
                )
                Text(
                    text = "已点亮 $lit / $total 格 · 共 $characters 位使徒",
                    fontSize = 10.5.sp,
                    color = Tc.colors.ink2,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 11.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(Tc.colors.gold, "+$bonus%", "加成")
                    Chip(Tc.colors.brand, "$gold", "根")
                }            }
        }
    }
}

/** `.chip`：白底胶囊 + 小圆点 + 粗体数字。 */
@Composable
private fun Chip(dot: Color, value: String, unit: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Tc.colors.surface)
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Tc.colors.ink,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = " $unit",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tc.colors.ink2,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun pastel(argb: Long): Color =
    if (Tc.colors.isDark) Tc.colors.surface2 else Color(argb)

@Composable
private fun FeatureTile(
    iconBg: Color,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    glyph: FuncGlyph? = null,
    badge: String? = null,
    badgeIsNew: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Tc.colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                if (glyph != null) {
                    FuncGlyphIcon(glyph, Color.White, Modifier.size(34.dp))
                } else {
                    Text(emoji.orEmpty(), fontSize = 22.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (badge != null) {
                val bg = when {
                    muted -> Tc.colors.surface2
                    badgeIsNew -> Tc.colors.ok.copy(alpha = 0.15f)
                    else -> Tc.colors.brandSoft
                }
                val fg = when {
                    muted -> Tc.colors.ink3
                    badgeIsNew -> Tc.colors.ok
                    else -> Tc.colors.brand
                }
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    color = fg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .padding(horizontal = 6.dp, vertical = 2.5.dp),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.1).sp,
            color = if (muted) Tc.colors.ink2 else Tc.colors.ink,
        )
        Text(
            text = desc,
            fontSize = 10.5.sp,
            color = Tc.colors.ink3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------- 功能清单与管理 ----------------

private data class HomeFeature(
    val id: String,
    val title: String,
    val desc: String,
    val iconArgb: Long,
    val glyph: FuncGlyph? = null,
    val emoji: String? = null,
    val badge: String? = null,
    val badgeIsNew: Boolean = false,
    val muted: Boolean = false,
    val route: String? = null,
    val toast: String? = null,
)

private val HOME_FEATURES = listOf(
    HomeFeature("crayon", "蜡笔涂色", "使徒棋盘解锁进度", 0xFFE8845F, glyph = FuncGlyph.CRAYONS, route = "apostle"),
    HomeFeature("stats", "涂色统计", "每层每属性明细", 0xFF5FA87F, glyph = FuncGlyph.CHIBI, route = "stats"),
    HomeFeature("pet", "宠物派遣", "最优派遣方案", 0xFF7BB05E, glyph = FuncGlyph.PAW, badge = "BETA", badgeIsNew = true, route = "petdispatch"),
    HomeFeature("battlepower", "战斗力计算", "战力换算 · 待接入", 0xFF6E8FCB, glyph = FuncGlyph.KEY_SWORD, badge = "规划", muted = true, toast = "战斗力计算 · 规划中"),
    HomeFeature("damage", "伤害计算", "BETA · 待接入", 0xFFFBEAE3, emoji = "💥", badge = "规划", muted = true, toast = "伤害计算 · 规划中"),
    HomeFeature("crayonimport", "蜡笔导入", "截图识别 · 自动写入进度", 0xFFEDF2F8, emoji = "🖼️", badge = "BETA", badgeIsNew = true, route = "crayonimport"),
    HomeFeature("arena", "竞技场 auto", "手机自己在打竞技场", 0xFFF1EAFB, emoji = "🤖", badge = "规划", muted = true, toast = "竞技场 auto · 规划中"),
)

@Composable
private fun HomeFeatureTile(
    vm: AppViewModel,
    feature: HomeFeature,
    modifier: Modifier,
    onNavigate: (String) -> Unit,
) {
    FeatureTile(
        glyph = feature.glyph,
        emoji = feature.emoji,
        iconBg = if (Tc.colors.isDark) Tc.colors.surface2 else Color(feature.iconArgb),
        title = feature.title,
        desc = feature.desc,
        badge = feature.badge,
        badgeIsNew = feature.badgeIsNew,
        muted = feature.muted,
        modifier = modifier,
    ) {
        val route = feature.route
        if (route != null) onNavigate(route) else vm.showToast(feature.toast.orEmpty())
    }
}

@Composable
private fun FeatureManageDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val defaultIds = HOME_FEATURES.map { it.id }
    val order = vm.resolvedFeatureOrder(defaultIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tc.colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "功能管理",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tc.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "恢复默认",
                    fontSize = 11.sp,
                    color = Tc.colors.brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.resetFeatures() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "用箭头调整顺序，右侧开关控制首页是否显示。",
                    fontSize = 10.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                order.forEachIndexed { index, id ->
                    val f = HOME_FEATURES.firstOrNull { it.id == id } ?: return@forEachIndexed
                    val hidden = vm.hiddenFeatures.contains(id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (Tc.colors.isDark) Tc.colors.surface2 else Color(f.iconArgb),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (f.glyph != null) {
                                FuncGlyphIcon(f.glyph, Color.White, Modifier.size(17.dp))
                            } else {
                                Text(f.emoji.orEmpty(), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = f.title,
                            fontSize = 12.sp,
                            color = if (hidden) Tc.colors.ink3 else Tc.colors.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        StepButton("↑", enabled = index > 0) { vm.moveFeature(id, defaultIds, -1) }
                        StepButton("↓", enabled = index < order.lastIndex) { vm.moveFeature(id, defaultIds, 1) }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (hidden) Tc.colors.surface2 else Tc.colors.brandSoft)
                                .clickable { vm.toggleFeatureVisible(id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (hidden) "已隐藏" else "显示中",
                                fontSize = 9.5.sp,
                                color = if (hidden) Tc.colors.ink3 else Tc.colors.brand,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Tc.colors.brand, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) Tc.colors.surface2 else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) Tc.colors.ink2 else Tc.colors.line)
    }
}

@Composable
private fun FeatureGrid(
    vm: AppViewModel,
    features: List<HomeFeature>,
    onNavigate: (String) -> Unit,
) {
    features.chunked(2).forEach { rowItems ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            rowItems.forEach { feature ->
                HomeFeatureTile(vm, feature, Modifier.weight(1f)) { onNavigate(it) }
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(11.dp))
    }
}
