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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.TagPill
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.darkenFilter
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

    PageColumn {
        Spacer(Modifier.height(10.dp))
        BackButton(label = "使徒", onClick = onBack)

        // .detail-hero{padding:6px 0 20px} gap 15
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

        // .tierseg{gap:8px;margin-bottom:14px} 44dp 高、圆角 14、选中品牌底白字
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
                modifier = Modifier.fillMaxWidth(),
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
                Spacer(Modifier.height(13.dp))
                BoardGrid(vm, ch, layer)
                Spacer(Modifier.height(13.dp))
                Text(
                    text = "点击格子逐格涂色；未点亮的格子显示为灰色。",
                    fontSize = 9.5.sp,
                    color = Tc.colors.ink3,
                )
            }
        }
    }
}

/** .board：30dp 方格、3dp 间距、整体居中。 */
@Composable
private fun BoardGrid(vm: AppViewModel, ch: Character, layer: Layer) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (ri in 0 until layer.rows.size) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ci in 0 until layer.width) {
                        BoardCell(vm, layer, ri, ci, 30.dp)
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
    val base = Modifier.size(size).clip(shape)

    when {
        cell == null || cell.sym == '.' -> Box(base)
        cell.attr != null -> {
            val on = cell.slotId?.let { vm.lit.contains(it) } == true
            Box(
                modifier = base
                    .background(Tc.colors.surface2)
                    .padding(2.dp)
                    .clickable { cell.slotId?.let { vm.toggleSlot(it) } },
            ) {
                AssetImage(
                    path = "cells/${cell.attr?.imgName}.webp",
                    contentDescription = cell.attr?.label,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (on) null else darkenFilter(0.50f),
                )
            }
        }
        cell.purple != null -> Box(
            modifier = base.background(Color(0xFFEFE7FA)).padding(2.dp),
        ) {
            AssetImage(
                path = "cells/cell_purple_${cell.purple?.name?.lowercase()}.webp",
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f,
            )
        }
        cell.sym == 'S' || cell.sym == 'E' -> {
            val start = cell.sym == 'S'
            Box(
                modifier = base.background(
                    Brush.linearGradient(
                        if (start) {
                            listOf(Color(0xFF7BC08D), Color(0xFF5FA875))
                        } else {
                            listOf(Color(0xFFE88572), Color(0xFFCE6455))
                        },
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (start) "起" else "终",
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }
        cell.sym == 'P' -> Box(base.background(Color(0xFFEFE7FA)))
        else -> Box(base.background(Tc.colors.surface2))
    }
}
