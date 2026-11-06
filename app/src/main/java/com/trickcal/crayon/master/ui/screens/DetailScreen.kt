package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.data.Character
import com.trickcal.crayon.master.data.Layer
import com.trickcal.crayon.master.data.Tier
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.TagPill
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.Tc
import kotlin.math.roundToInt

@Composable
fun DetailScreen(vm: AppViewModel, charId: String, onBack: () -> Unit) {
    val ch = vm.data.characterById[charId]
    if (ch == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到该使徒", color = Tc.colors.ink3)
        }
        return
    }

    val prog = remember(vm.lit, ch) { BoardEngine.charProgress(ch, vm.lit) }
    val stats = remember(vm.lit, ch) { BoardEngine.tierStats(ch, vm.lit) }
    val tierIndex = (vm.detailTier - 1).coerceIn(0, 2)
    val layer = ch.layers.getOrNull(tierIndex)
    val tierInfo = Tier.byN(layer?.tier ?: 1)
    val layerStat = stats.firstOrNull { it.tier == layer?.tier }

    // 固定一屏布局：卡片与棋盘各自吃掉剩余空间，任何分辨率下都不会产生滚动
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        BackButton(label = "使徒", onClick = onBack)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        ) {
            AssetImage(
                path = "avatars/${ch.avatarKey}.webp",
                modifier = Modifier
                    .size(78.dp)
                    .shadow(4.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ch.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    color = Tc.colors.ink,
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagPill(
                        text = "${ch.personality.label}属性",
                        color = Color(ch.personality.argb),
                        background = Color(ch.personality.bgArgb),
                        fontSize = 10,
                    )
                    TagPill(text = "${prog.lit}/${prog.total} 格", fontSize = 10)
                    TagPill(text = "${(prog.pct * 100).roundToInt()}%", fontSize = 10)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Tier.ALL.forEachIndexed { index, t ->
                val on = index == tierIndex
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .then(if (on) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
                        .clip(shape)
                        .background(if (on) Tc.colors.brand else Tc.colors.surface)
                        .clickable { vm.detailTier = t.n },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t.label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else Tc.colors.ink2,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (layer != null) {
            TcCard(
                padding = 15.dp,
                shape = RoundedCornerShape(22.dp),
                elevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Tc.colors.brandSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tierInfo.n.toString(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Tc.colors.brand,
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = tierInfo.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ink,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${layerStat?.lit ?: 0}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ok,
                        )
                        Text(
                            text = "/${layerStat?.total ?: 0} · +${(layerStat?.lit ?: 0) * tierInfo.bonus}%",
                            fontSize = 11.sp,
                            color = Tc.colors.ink3,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 棋盘按「卡片剩余宽高」实时定尺寸：取高度与宽度的较小约束，铺满即止
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val gap = 3.dp
                        val rows = layer.rows.size.coerceAtLeast(1)
                        val cols = layer.width.coerceAtLeast(1)
                        // 留 2dp 余量 + 向下取整到整 dp：宁可棋盘略小，也不让最后一行被父约束压缩
                        val availH = (maxHeight - 2.dp).coerceAtLeast(1.dp)
                        val raw = minOf(
                            (maxWidth - gap * (cols - 1)) / cols,
                            (availH - gap * (rows - 1)) / rows,
                        )
                        val cell = kotlin.math.floor(raw.value).toInt().dp.coerceIn(1.dp, 64.dp)
                        BoardGrid(vm, layer, cell, gap)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "点击彩色格子逐格涂色；点亮后描金边。",
                    fontSize = 9.5.sp,
                    color = Tc.colors.ink3,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** 棋盘：格子边长与间距由外部按可用空间给定。 */
@Composable
private fun BoardGrid(vm: AppViewModel, layer: Layer, cell: Dp, gap: Dp) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (ri in 0 until layer.rows.size) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (ci in 0 until layer.width) {
                        BoardCell(vm, layer, ri, ci, cell)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(vm: AppViewModel, layer: Layer, row: Int, col: Int, size: Dp) {
    val cell = layer.cellAt(row, col)
    val shape = RoundedCornerShape(5.dp)
    // requiredSize：无视父级剩余高度，保证每格永远是长宽相等的正方形，不会被压扁
    val base = Modifier.requiredSize(size).clip(shape)

    if (cell == null || cell.sym == '.') {
        Box(base)
        return
    }

    when {
        cell.attr != null -> {
            val on = cell.slotId?.let { vm.lit.contains(it) } == true
            Box(
                modifier = base
                    .background(Tc.colors.surface2)
                    .border(
                        width = if (on) 2.dp else 0.dp,
                        color = if (on) Tc.colors.gold else Color.Transparent,
                        shape = shape,
                    )
                    .clickable { cell.slotId?.let { vm.toggleSlot(it) } },
                contentAlignment = Alignment.Center,
            ) {
                // 属性格始终显示素材原色，保证 0/N 时也能靠颜色分辨属性；点亮只加金色描边
                AssetImage(
                    path = "cells/${cell.attr?.imgName}.webp",
                    contentDescription = cell.attr?.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        cell.purple != null -> Box(
            modifier = base.background(Color(0xFFEFE7FA)),
            contentAlignment = Alignment.Center,
        ) {
            AssetImage(
                path = "cells/cell_purple_${cell.purple?.name?.lowercase()}.webp",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 0.55f,
            )
        }
        cell.sym == 'W' -> Box(base, contentAlignment = Alignment.Center) {
            AssetImage(
                path = "cells/cell_white.webp",
                contentDescription = "路径",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        // 起点 / 终点：只显示图标本体，不加底色块
        cell.sym == 'S' || cell.sym == 'E' -> {
            val start = cell.sym == 'S'
            Box(base, contentAlignment = Alignment.Center) {
                AssetImage(
                    path = if (start) "cells/cell_start.webp" else "cells/cell_end.webp",
                    contentDescription = if (start) "起点" else "终点",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        cell.sym == 'P' -> Box(base.background(Color(0xFFEFE7FA)))
        else -> Box(base.background(Tc.colors.surface2))
    }
}
