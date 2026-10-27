package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.data.Tier
import com.trickcal.crayon.master.domain.AttrStat
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.ProgressRing
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.brandGradient
import com.trickcal.crayon.master.ui.theme.Tc
import kotlin.math.roundToInt

/** 内容完整优先：矩阵卡给足高度，屏幕放不下时整页滚动，避免行被压缩裁切。 */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats = remember(vm.lit) { BoardEngine.globalStats(vm.data, vm.lit) }

    PageColumn {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                Text(
                    text = "涂色统计",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    color = Tc.colors.ink,
                )
                Text(
                    text = "${vm.data.characters.size} 位使徒 · ${stats.total} 格",
                    fontSize = 11.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            // .hero2：主位已用金蜡笔 + 次位格子环
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                background = brandGradient(),
                elevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssetImage("ui/crayon.webp", modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stats.gold.toString(),
                                modifier = Modifier.alignByBaseline(),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1.6).sp,
                                color = Tc.colors.gold,
                            )
                            Text(
                                text = "根",
                                modifier = Modifier.alignByBaseline().padding(start = 9.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Tc.colors.ink3,
                            )
                        }
                        Text(
                            text = "已用金蜡笔",
                            fontSize = 10.5.sp,
                            color = Tc.colors.ink3,
                            modifier = Modifier.padding(start = 32.dp, top = 6.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.width(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressRing(
                            progress = stats.pct,
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp,
                            brush = Brush.linearGradient(
                                listOf(Tc.colors.brand, lerp(Tc.colors.brand, Tc.colors.gold, 0.58f)),
                            ),
                        ) {
                            Row {
                                Text(
                                    text = stats.lit.toString(),
                                    modifier = Modifier.alignByBaseline(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.7).sp,
                                    color = Tc.colors.ink,
                                )
                                Text(
                                    text = "/${stats.total}",
                                    modifier = Modifier.alignByBaseline().padding(start = 1.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Tc.colors.ink3,
                                )
                            }
                        }
                        Text(
                            text = "已解锁格子",
                            fontSize = 9.5.sp,
                            letterSpacing = 0.2.sp,
                            color = Tc.colors.ink3,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // .scard 按属性
            TcCard(
                padding = 13.dp,
                shape = RoundedCornerShape(15.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Attr.ORDER.forEach { attr ->
                    val d = stats.byAttr[attr] ?: AttrStat()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssetImage("cells/${attr.imgName}.webp", modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = attr.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Tc.colors.ink2,
                            maxLines = 1,
                            modifier = Modifier.width(40.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Tc.colors.surface2),
                        ) {
                            val f = if (d.total == 0) 0f else d.lit.toFloat() / d.total
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(f)
                                    .fillMaxHeight()
                                    .background(Tc.colors.brand),
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "${d.lit}/${d.total}",
                            fontSize = 10.sp,
                            color = Tc.colors.ink3,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(50.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "+${d.bonus}%",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.gold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(50.dp),
                        )
                    }
                }
            }

            // .scard 层 × 属性：最小高度保证 5 行完整显示
            TcCard(
                padding = 13.dp,
                shape = RoundedCornerShape(15.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 232.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(26.dp))
                        Spacer(Modifier.width(6.dp))
                        Tier.ALL.forEach { t ->
                            val d = stats.byTier[t.n] ?: AttrStat()
                            val p = if (d.total == 0) 0 else (d.lit * 100f / d.total).roundToInt()
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = t.label,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Tc.colors.ink3,
                                    lineHeight = 10.sp,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$p%",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Tc.colors.brand,
                                    lineHeight = 10.sp,
                                )
                            }
                        }
                    }

                    Attr.ORDER.forEach { attr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                                AssetImage("cells/${attr.imgName}.webp", modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Tier.ALL.forEach { t ->
                                val c = stats.byTierAttr[t.n]?.get(attr) ?: AttrStat()
                                MatrixCell(c, Modifier.weight(1f))
                                if (t.n != Tier.ALL.last().n) Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(c: AttrStat, modifier: Modifier = Modifier) {
    val done = c.total > 0 && c.lit == c.total
    val zero = c.lit == 0
    val f = if (c.total == 0) 0f else c.lit.toFloat() / c.total
    val bg = if (done) lerp(Tc.colors.surface, Tc.colors.ok, 0.14f) else Tc.colors.surface2
    val fg = when {
        done -> Tc.colors.ok
        zero -> Tc.colors.ink3
        else -> Tc.colors.ink
    }
    val subFg = if (done) lerp(Tc.colors.ink3, Tc.colors.ok, 0.38f) else Tc.colors.ink3

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = c.lit.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 12.sp,
                color = fg,
            )
            Text(
                text = "/${c.total}",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 9.sp,
                color = subFg,
                modifier = Modifier.padding(start = 1.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Tc.colors.ink.copy(alpha = 0.07f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .background(if (done) Tc.colors.ok else Tc.colors.brand),
            )
        }
    }
}
