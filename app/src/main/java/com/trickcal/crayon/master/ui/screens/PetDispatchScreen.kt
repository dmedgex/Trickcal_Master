package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.data.PdTask
import com.trickcal.crayon.master.data.Pet
import com.trickcal.crayon.master.data.RewardTier
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.AssetImage
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.SegmentedControl
import com.trickcal.crayon.master.ui.components.TagPill
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.components.darkenFilter
import com.trickcal.crayon.master.ui.components.dimFilter
import com.trickcal.crayon.master.ui.theme.Tc

private val PET_SKILLS = listOf("迟钝", "活泼", "亲密", "敏锐", "体贴", "自信")

/** 三个模块：地区配置 / 宠物配置 / 计算方案，各自独立滚动。 */
private val MODULES = listOf("地区配置", "宠物配置", "计算方案")

@Composable
fun PetDispatchScreen(vm: AppViewModel, onBack: () -> Unit) {
    var module by remember { mutableIntStateOf(0) }
    val outcome = vm.pdOutcome

    // 计算完成自动切到「计算方案」
    LaunchedEffect(outcome) {
        if (outcome != null) module = 2
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                BackButton(label = "工具", onClick = onBack)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "宠物派遣",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                        color = Tc.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${vm.selectedPets.size} 只 · ${vm.taskCount} 任务",
                        fontSize = 11.sp,
                        color = Tc.colors.brand,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = MODULES,
                    selectedIndex = module,
                    modifier = Modifier.fillMaxWidth(),
                ) { module = it }
                Spacer(Modifier.height(12.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when (module) {
                    0 -> RegionModule(vm)
                    1 -> PetModule(vm)
                    else -> ResultModule(vm)
                }
            }
        }

        // 底部固定操作条
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Tc.colors.paper)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tc.colors.brand)
                    .clickable { vm.runPetDispatch() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (outcome?.ok == true) "重新计算" else "计算最优方案",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

// ---------------- 模块一：地区配置 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionModule(vm: AppViewModel) {
    val region = vm.data.regions.firstOrNull { it.name == vm.region }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 80.dp),
    ) {
        TcCard(
            padding = 14.dp,
            shape = RoundedCornerShape(18.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("派遣区域", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                vm.data.regions.forEach { r ->
                    val on = r.name == vm.region
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) Tc.colors.brand else Tc.colors.surface2)
                            .clickable { vm.selectRegion(r.name) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = r.name,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Color.White else Tc.colors.ink2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("执行任务数", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Spacer(Modifier.weight(1f))
                Text("${vm.taskCount} 个", fontSize = 11.sp, color = Tc.colors.brand)
            }
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf("1", "2", "3", "4", "5"),
                selectedIndex = (vm.taskCount - 1).coerceIn(0, 4),
                modifier = Modifier.fillMaxWidth(),
            ) { vm.selectTaskCount(it + 1) }
        }

        Spacer(Modifier.height(10.dp))

        TcCard(
            padding = 14.dp,
            shape = RoundedCornerShape(18.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("任务加成特性", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
            Text(
                text = "默认值是常见刷法，按你游戏内实际刷到的点选即可。",
                fontSize = 9.5.sp,
                color = Tc.colors.ink3,
                modifier = Modifier.padding(top = 4.dp),
            )
            region?.tasks?.forEachIndexed { index, task ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(Tc.colors.line),
                    )
                }
                TaskRow(
                    task = task,
                    skills = vm.effectiveBonus(task.id),
                    onToggleSkill = { vm.toggleTaskSkill(task.id, it) },
                )
            }
        }
    }
}

/** 单个任务：任务名 + 六个特性直接铺开，点一下切换。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(task: PdTask, skills: List<String>, onToggleSkill: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = task.task,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tc.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "·",
                fontSize = 11.sp,
                color = Tc.colors.ink3,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = task.area,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Tc.colors.ink2,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PET_SKILLS.forEach { s ->
                val on = skills.contains(s)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Tc.colors.brand else Tc.colors.surface2)
                        .clickable { onToggleSkill(s) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s,
                        fontSize = 10.5.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color.White else Tc.colors.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------- 模块二：宠物配置 ----------------

@Composable
private fun PetModule(vm: AppViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 84.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            ) {
                Text("拥有宠物", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${vm.selectedPets.size}/${vm.data.pets.size}",
                    fontSize = 10.5.sp,
                    color = Tc.colors.brand,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (vm.selectedPets.size == vm.data.pets.size) "清空" else "全选",
                    fontSize = 11.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Tc.colors.surface2)
                        .clickable { vm.setAllPets(vm.selectedPets.size != vm.data.pets.size) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        items(vm.data.pets, key = { it.id }) { pet ->
            PetCard(
                pet = pet,
                selected = vm.selectedPets.contains(pet.id),
            ) { vm.togglePet(pet.id) }
        }
    }
}

/** 对齐旧版 PetDispatchPetCard：竖版比例 + 图片 + 名字 + 品质 + 特性。 */
@Composable
private fun PetCard(pet: Pet, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Tc.colors.brandSoft else Tc.colors.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Tc.colors.brand else Tc.colors.line,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            AssetImage(
                path = "pets/${pet.id}.webp",
                contentDescription = pet.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (selected) null else darkenFilter(0.62f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = pet.name,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) Tc.colors.ink else Tc.colors.ink2,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = pet.rarityLabel,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = Color(pet.rarityArgb),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (pet.skills.isEmpty()) {
                "无特性"
            } else {
                pet.skills.joinToString(" ") { "${it.name}(${it.level})" }
            },
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Tc.colors.ink3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------- 模块三：计算方案 ----------------

@Composable
private fun ResultModule(vm: AppViewModel) {
    val outcome = vm.pdOutcome
    val scroll = rememberScrollState()

    if (outcome == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            TcCard(
                padding = 18.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = "还没有计算结果",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tc.colors.ink,
                )
                Text(
                    text = "先确认地区与宠物，点底部「计算最优方案」。",
                    fontSize = 11.sp,
                    color = Tc.colors.ink3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 84.dp),
    ) {
        if (!outcome.ok) {
            TcCard(
                padding = 16.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(outcome.message, fontSize = 12.sp, color = Tc.colors.warn, lineHeight = 18.sp)
            }
            return@Column
        }
        val sol = outcome.solution ?: return@Column
        ResultBlock(
            assignments = sol.assignments,
            totalScore = sol.totalScore,
            borrowed = sol.borrowed,
            totalPets = sol.totalPets,
            summary = sol.summary,
            allSpecial = sol.allSpecial,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultBlock(
    assignments: List<com.trickcal.crayon.master.domain.Assignment>,
    totalScore: Int,
    borrowed: Int,
    totalPets: Int,
    summary: com.trickcal.crayon.master.domain.Summary,
    allSpecial: Boolean,
) {
    TcCard(
        padding = 16.dp,
        shape = RoundedCornerShape(20.dp),
        elevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("总得分", fontSize = 10.5.sp, color = Tc.colors.ink3)
                Text(
                    text = totalScore.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                    color = Tc.colors.brand,
                )
            }
            Text(
                text = "借用 $borrowed 只\n使用 $totalPets 只",
                fontSize = 10.5.sp,
                color = Tc.colors.ink2,
                lineHeight = 17.sp,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val items = listOf(
                RewardTier.SPECIAL to summary.special,
                RewardTier.FIRST to summary.first,
                RewardTier.SECOND to summary.second,
                RewardTier.THIRD to summary.third,
                RewardTier.FOURTH to summary.fourth,
            ).filter { it.second > 0 }
            if (items.isEmpty()) {
                TagPill("无奖励")
            } else {
                items.forEach { (tier, count) ->
                    TagPill(
                        text = "${tier.label} × $count",
                        color = Color(tier.colorArgb),
                        background = Color(tier.bgArgb),
                    )
                }
            }
            if (allSpecial) {
                TagPill("✦ 全特阶", color = Color(0xFFC89A3F), background = Color(0xFFFCF3DF))
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    assignments.forEachIndexed { index, a ->
        TcCard(
            padding = 14.dp,
            shape = RoundedCornerShape(16.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${a.task.task}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tc.colors.ink,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = a.task.area,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Tc.colors.ink2,
                            maxLines = 1,
                        )
                        Text(
                            text = "·",
                            fontSize = 10.5.sp,
                            color = Tc.colors.ink3,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Text(
                            text = if (a.task.bonusSkills.isEmpty()) {
                                "无加成特性"
                            } else {
                                a.task.bonusSkills.joinToString(" / ")
                            },
                            fontSize = 9.5.sp,
                            color = Tc.colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TagPill(
                    text = a.tier.label,
                    color = Color(a.tier.colorArgb),
                    background = Color(a.tier.bgArgb),
                )
            }
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                a.team.forEach { member ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Tc.colors.surface2)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssetImage(
                            path = "pets/${member.pet.id}.webp",
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(member.pet.name, fontSize = 9.5.sp, color = Tc.colors.ink2)
                        if (member.borrowed) {
                            Text(
                                text = "借",
                                fontSize = 8.sp,
                                color = Tc.colors.warn,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "${a.score} 分",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tc.colors.brand,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
