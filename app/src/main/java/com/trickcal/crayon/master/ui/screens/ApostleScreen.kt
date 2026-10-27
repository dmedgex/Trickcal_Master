package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.data.Character
import com.trickcal.crayon.master.data.Personality
import com.trickcal.crayon.master.data.Tier
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.domain.QuickPaintTarget
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.BatchAction
import com.trickcal.crayon.master.ui.BrowseMode
import com.trickcal.crayon.master.ui.DisplayMode
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.SearchField
import com.trickcal.crayon.master.ui.components.SegmentedControl
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.darkenFilter
import com.trickcal.crayon.master.ui.components.dimFilter
import com.trickcal.crayon.master.ui.theme.Tc
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ApostleScreen(vm: AppViewModel, onOpenDetail: (String) -> Unit) {
    val list = remember(vm.filters, vm.data, vm.displayMode, vm.lit) {
        val base = BoardEngine.filterCharacters(vm.data, vm.filters)
        if (vm.displayMode == DisplayMode.COMPACT) {
            BoardEngine.sortForCompactDisplay(base, vm.filters.tiers, vm.filters.attrs, vm.lit)
        } else {
            base
        }
    }
    val stats = remember(vm.lit) { BoardEngine.globalStats(vm.data, vm.lit) }
    val isPaint = vm.browseMode == BrowseMode.PAINT
    var showBatch by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // .listhead 吸顶：搜索栏 + 模式条 + 三行筛选
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Tc.colors.paper)
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        value = vm.filters.query,
                        placeholder = "搜索使徒",
                        onValueChange = vm::setQuery,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(9.dp))
                    LabeledIconButton(
                        symbol = if (vm.displayMode == DisplayMode.DETAIL) "▦" else "▤",
                        label = vm.displayMode.label,
                    ) {
                        val next = if (vm.displayMode == DisplayMode.DETAIL) {
                            DisplayMode.COMPACT
                        } else {
                            DisplayMode.DETAIL
                        }
                        vm.changeDisplayMode(next)
                    }
                    Spacer(Modifier.width(6.dp))
                    LabeledIconButton(label = "批量", useCrayon = true) { showBatch = true }
                }
                Spacer(Modifier.height(11.dp))
                SegmentedControl(
                    options = listOf(BrowseMode.DETAIL.label, BrowseMode.PAINT.label),
                    selectedIndex = if (isPaint) 1 else 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { index ->
                    vm.changeBrowseMode(if (index == 1) BrowseMode.PAINT else BrowseMode.DETAIL)
                }
                Spacer(Modifier.height(14.dp))
                FilterSection(vm)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "${list.size} 位使徒" +
                        if (list.size != vm.data.characters.size) " / 共 ${vm.data.characters.size}" else "",
                    fontSize = 11.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "总进度 ${stats.lit}/${stats.total}",
                    fontSize = 11.sp,
                    color = Tc.colors.ink3,
                )
            }
        }

        if (list.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有匹配的使徒", fontSize = 13.sp, color = Tc.colors.ink3)
                }
            }
        } else if (vm.displayMode == DisplayMode.DETAIL) {
            // .charlist：两列，gap 9
            items(list.chunked(2)) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    row.forEach { ch ->
                        CharacterCard(vm, ch, Modifier.weight(1f)) { onOpenDetail(ch.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
            }
        } else {
            items(list.chunked(4)) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    row.forEach { ch ->
                        CompactCell(vm, ch, Modifier.weight(1f)) { onOpenDetail(ch.id) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showBatch) {
        BatchPaintDialog(vm, list) { showBatch = false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchPaintDialog(vm: AppViewModel, list: List<Character>, onDismiss: () -> Unit) {
    var action by remember { mutableStateOf(BatchAction.UNLOCK) }
    var tiers by remember { mutableStateOf(emptySet<Int>()) }
    var attrs by remember { mutableStateOf(emptySet<Attr>()) }
    val preview = remember(action, tiers, attrs, vm.lit, list) {
        vm.batchPreview(action, tiers, attrs, list)
    }
    val ready = tiers.isNotEmpty() && attrs.isNotEmpty()
    val canExecute = ready && preview.ids.isNotEmpty()

    val previewText = when {
        !ready -> "请至少选择一个层数和一个格子属性。"
        preview.ids.isEmpty() -> "当前条件下没有可执行的金蜡笔格子。"
        else -> "${action.verb} ${preview.charCount} 个使徒的 ${preview.slotCount} 个金蜡笔格子"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Tc.colors.paper, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        text = "一键涂色",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                        color = Tc.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tc.colors.surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", fontSize = 14.sp, color = Tc.colors.ink2)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TcCard(
                        padding = 13.dp,
                        shape = RoundedCornerShape(15.dp),
                        elevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "操作类型",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ink,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BatchAction.entries.forEach { a ->
                                BatchPill(
                                    label = a.label,
                                    on = action == a,
                                    modifier = Modifier.weight(1f),
                                ) { action = a }
                            }
                        }
                    }

                    TcCard(
                        padding = 13.dp,
                        shape = RoundedCornerShape(15.dp),
                        elevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "层数",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ink,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Tier.ALL.forEach { t ->
                                BatchPill(
                                    label = t.label,
                                    on = tiers.contains(t.n),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    tiers = if (tiers.contains(t.n)) tiers - t.n else tiers + t.n
                                }
                            }
                        }

                        Text(
                            text = "格子属性",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ink,
                            modifier = Modifier.padding(top = 13.dp, bottom = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Attr.ORDER.forEach { a ->
                                val on = attrs.contains(a)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable {
                                            attrs = if (attrs.contains(a)) attrs - a else attrs + a
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AssetImage(
                                        path = "cells/${a.imgName}.webp",
                                        contentDescription = a.label,
                                        modifier = Modifier.size(44.dp),
                                        alpha = if (on) 1f else 0.4f,
                                    )
                                }
                            }
                        }
                    }

                    TcCard(
                        padding = 13.dp,
                        shape = RoundedCornerShape(15.dp),
                        elevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "预览",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tc.colors.ink,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Text(
                            text = previewText,
                            fontSize = 12.sp,
                            color = if (ready && preview.ids.isNotEmpty()) {
                                Tc.colors.ink2
                            } else {
                                Tc.colors.ink3
                            },
                            lineHeight = 18.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canExecute) Tc.colors.brand else Tc.colors.ink.copy(alpha = 0.10f),
                        )
                        .clickable(enabled = canExecute) {
                            vm.applyBatch(action, tiers, attrs, list)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = action.confirmLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canExecute) Color.White else Tc.colors.ink3,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchPill(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(Tc.colors.surface)
            .border(
                width = if (on) 1.5.dp else 1.dp,
                color = if (on) Tc.colors.brand else Tc.colors.ink.copy(alpha = 0.14f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
            color = if (on) Tc.colors.brand else Tc.colors.ink2,
        )
    }
}

/** 图标 + 文字的胶囊按钮：原版只有一个符号，手机上无法判断用途。 */
@Composable
private fun LabeledIconButton(
    label: String,
    symbol: String? = null,
    useCrayon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Tc.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (useCrayon) {
            AssetImage("ui/crayon.webp", modifier = Modifier.size(14.dp))
        } else if (symbol != null) {
            Text(symbol, fontSize = 12.sp, color = Tc.colors.ink2)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tc.colors.ink2,
        )
    }
}

@Composable
private fun FilterSection(vm: AppViewModel) {
    val f = vm.filters
    Column {
        // .tierrow：3 等分，42dp 高，圆角 14，边框 ink 14%；选中 1.5dp 品牌描边
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tier.ALL.forEach { t ->
                val on = f.tiers.contains(t.n)
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(shape)
                        .background(Tc.colors.surface)
                        .border(
                            width = if (on) 1.5.dp else 1.dp,
                            color = if (on) Tc.colors.brand else Tc.colors.ink.copy(alpha = 0.14f),
                            shape = shape,
                        )
                        .clickable { vm.toggleTierFilter(t.n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t.label,
                        fontSize = 12.5.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) Tc.colors.brand else Tc.colors.ink2,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // .iconrow：5 等分正方形，图标 88%，未选 opacity .4，无底色
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Attr.ORDER.forEach { a ->
                val on = f.attrs.contains(a)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable { vm.toggleAttrFilter(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    AssetImage(
                        path = "cells/${a.imgName}.webp",
                        contentDescription = a.label,
                        modifier = Modifier.size(44.dp),
                        alpha = if (on) 1f else 0.4f,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Personality.ORDER.forEach { p ->
                val on = f.personality == p
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable { vm.togglePersonality(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    AssetImage(
                        path = "pers/${p.imgName}.webp",
                        contentDescription = p.label,
                        modifier = Modifier.size(44.dp),
                        alpha = if (on) 1f else 0.4f,
                    )
                }
            }
        }
        if (f.activeCount > 0) {
            Text(
                text = "清空筛选",
                fontSize = 10.5.sp,
                color = Tc.colors.ink3,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { vm.clearFilters() },
            )
        }
    }
}

private fun handleCardClick(
    vm: AppViewModel,
    ch: Character,
    target: QuickPaintTarget?,
    onOpen: () -> Unit,
) {
    val paint = vm.browseMode == BrowseMode.PAINT
    when {
        !paint -> onOpen()
        target != null -> vm.applyQuickPaint(ch.id, target)
        else -> vm.showToast("请先在上方各选择 1 个层与格子")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterCard(
    vm: AppViewModel,
    ch: Character,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val lit = vm.lit
    val prog = remember(lit, ch) { BoardEngine.charProgress(ch, lit) }
    val target = remember(lit, vm.filters, ch) {
        BoardEngine.quickPaintTarget(ch, vm.filters.tiers, vm.filters.attrs, lit)
    }
    val dim = target?.shouldUnlock == true
    val paint = vm.browseMode == BrowseMode.PAINT
    val pers = Color(ch.personality.argb)
    val persBg = if (Tc.colors.isDark) {
        lerp(Tc.colors.surface, pers, 0.18f)
    } else {
        Color(ch.personality.bgArgb)
    }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .then(if (Tc.colors.isDark) Modifier else Modifier.shadow(2.dp, shape, clip = false))
            .clip(shape)
            .background(persBg)
            .drawBehind {
                drawRect(color = pers.copy(alpha = 0.7f), size = Size(3.dp.toPx(), size.height))
            }
            .border(1.dp, pers.copy(alpha = 0.2f), shape)
            .clickable { handleCardClick(vm, ch, target, onOpen) }
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (paint) Modifier.clickable { handleCardClick(vm, ch, target, onOpen) } else Modifier,
                        ),
                ) {
                    AssetImage(
                        path = "avatars/${ch.avatarKey}.webp",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (dim) dimFilter() else null,
                        alpha = if (dim) 0.85f else 1f,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(
                        text = ch.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.1).sp,
                        lineHeight = 16.sp,
                        color = Tc.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "进度 ${prog.lit}/${prog.total}",
                        fontSize = 10.5.sp,
                        color = Tc.colors.ink3,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    text = if (paint) "✎" else "›",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tc.colors.brand.copy(alpha = 0.75f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(lerp(Tc.colors.line, pers, 0.22f)),
            )

            ch.layers.forEachIndexed { index, layer ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Text(
                    text = Tier.byN(layer.tier).label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tc.colors.ink2,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    layer.slots.forEach { cell ->
                        val on = cell.slotId?.let { lit.contains(it) } == true
                        AssetImage(
                            path = "cells/${cell.attr!!.imgName}.webp",
                            contentDescription = cell.attr?.label,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { cell.slotId?.let { vm.toggleSlot(it) } },
                            colorFilter = if (on) null else darkenFilter(0.52f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCell(
    vm: AppViewModel,
    ch: Character,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val lit = vm.lit
    val prog = remember(lit, ch) { BoardEngine.charProgress(ch, lit) }
    val target = remember(lit, vm.filters, ch) {
        BoardEngine.quickPaintTarget(ch, vm.filters.tiers, vm.filters.attrs, lit)
    }
    val dim = target?.shouldUnlock == true
    val pers = Color(ch.personality.argb)
    val persBg = if (Tc.colors.isDark) {
        lerp(Tc.colors.surface, pers, 0.18f)
    } else {
        Color(ch.personality.bgArgb)
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(persBg)
            .border(1.5.dp, pers.copy(alpha = 0.3f), shape)
            .clickable { handleCardClick(vm, ch, target, onOpen) },
    ) {
        AssetImage(
            path = "avatars/${ch.avatarKey}.webp",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = if (dim) darkenFilter(0.62f) else null,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        // demo: color-mix(in srgb, var(--pers) 78%, #000)
                        listOf(Color.Transparent, lerp(pers, Color.Black, 0.22f)),
                    ),
                )
                .padding(top = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ch.name,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
        if (prog.total > 0 && prog.lit == prog.total) {
            Text(
                text = "✦",
                fontSize = 11.sp,
                color = Tc.colors.gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}
