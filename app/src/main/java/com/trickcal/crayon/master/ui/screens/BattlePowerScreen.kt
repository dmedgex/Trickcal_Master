package com.trickcal.crayon.master.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.domain.BattlePowerEngine
import com.trickcal.crayon.master.domain.BpForm
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.PageColumn
import com.trickcal.crayon.master.ui.components.SearchField
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.Tc
import java.text.NumberFormat
import java.util.Locale

private fun fmtInt(v: Double): String = NumberFormat.getIntegerInstance(Locale.US).format(v.toLong())

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
fun BattlePowerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val list = vm.data.bpCharacters
    val current = list.firstOrNull { it.uid == vm.bpUid } ?: list.firstOrNull()
    val result = current?.let { BattlePowerEngine.calc(it, vm.bpForm) }
    var query by remember { mutableStateOf("") }
    val matches = remember(query, list) {
        if (query.isBlank()) emptyList() else list.filter { it.name.contains(query, true) }.take(8)
    }

    PageColumn {
        Spacer(Modifier.height(10.dp))
        BackButton(label = "返回", onClick = onBack)
        Spacer(Modifier.height(12.dp))
        Text("战斗力计算", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
        Text(
            text = "数据版本 v1.1.5 · ${list.size} 位角色",
            fontSize = 11.5.sp,
            color = Tc.colors.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        Text("选择角色", fontSize = 10.5.sp, color = Tc.colors.ink3)
        Spacer(Modifier.height(5.dp))
        SearchField(
            value = query,
            placeholder = current?.name ?: "搜索角色名",
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tc.colors.surface),
            ) {
                matches.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.selectBpUid(c.uid)
                                query = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, fontSize = 12.sp, color = Tc.colors.ink)
                        Spacer(Modifier.weight(1f))
                        Text(c.attackType, fontSize = 10.sp, color = Tc.colors.ink3)
                    }
                }
            }
        }

        if (result != null && current != null) {
            Spacer(Modifier.height(12.dp))
            TcCard(
                padding = 16.dp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("最终战斗力", fontSize = 10.5.sp, color = Tc.colors.ink3)
                Text(
                    text = fmtInt(result.power),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.6).sp,
                    color = Tc.colors.brand,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "主攻击（${current.attackType}）" to fmtInt(result.mainAttack),
                    "双防合计" to fmtInt(result.defenseSum),
                    "坨格加成" to "${(result.asideBonus * 100).toInt() / 10.0}%",
                    "基础分" to fmtInt(result.baseScore),
                    "成长乘区" to ("×" + String.format(Locale.US, "%.4f", result.growth)),
                ).forEachIndexed { index, row ->
                    ResultRow(row.first, row.second, even = index % 2 == 1)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TcCard(padding = 16.dp, modifier = Modifier.fillMaxWidth()) {
            Text("面板属性", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink, modifier = Modifier.padding(bottom = 13.dp))
            Spacer(Modifier.height(10.dp))
            val f = vm.bpForm
            NumberField("物理攻击", f.attackPhysic) { vm.updateBpForm(f.copy(attackPhysic = it)) }
            NumberField("魔法攻击", f.attackMagic) { vm.updateBpForm(f.copy(attackMagic = it)) }
            NumberField("物理防御", f.defensePhysic) { vm.updateBpForm(f.copy(defensePhysic = it)) }
            NumberField("魔法防御", f.defenseMagic) { vm.updateBpForm(f.copy(defenseMagic = it)) }
            NumberField("生命值", f.hp) { vm.updateBpForm(f.copy(hp = it)) }
        }

        Spacer(Modifier.height(10.dp))
        TcCard(padding = 16.dp, modifier = Modifier.fillMaxWidth()) {
            Text("暴击与速度", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink, modifier = Modifier.padding(bottom = 13.dp))
            Spacer(Modifier.height(10.dp))
            val f = vm.bpForm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("暴击率", f.criticalRate, Modifier.weight(1f)) { vm.updateBpForm(f.copy(criticalRate = it)) }
                NumberField("暴击伤害", f.criticalMult, Modifier.weight(1f)) { vm.updateBpForm(f.copy(criticalMult = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("暴击抗性", f.criticalResist, Modifier.weight(1f)) {
                    vm.updateBpForm(f.copy(criticalResist = it))
                }
                NumberField("暴伤抗性", f.criticalMultResist, Modifier.weight(1f)) {
                    vm.updateBpForm(f.copy(criticalMultResist = it))
                }
            }
            NumberField("攻击速度", f.attackSpeed) { vm.updateBpForm(f.copy(attackSpeed = it)) }
        }

        Spacer(Modifier.height(10.dp))
        TcCard(padding = 16.dp, modifier = Modifier.fillMaxWidth()) {
            Text("技能等级", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink, modifier = Modifier.padding(bottom = 13.dp))
            Spacer(Modifier.height(10.dp))
            val f = vm.bpForm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("低年级", f.spLevel, Modifier.weight(1f)) { vm.updateBpForm(f.copy(spLevel = it)) }
                NumberField("高年级", f.ultLevel, Modifier.weight(1f)) { vm.updateBpForm(f.copy(ultLevel = it)) }
                NumberField("被动", f.passiveLevel, Modifier.weight(1f)) { vm.updateBpForm(f.copy(passiveLevel = it)) }
            }
            NumberField("坨格等级", f.asideGrade) { vm.updateBpForm(f.copy(asideGrade = it)) }
            Text(
                text = "≥ 2 时坨格系数生效（${String.format(Locale.US, "%.1f", (current?.asideValueA ?: 0.0) * 100)}%）",
                fontSize = 9.5.sp,
                color = Tc.colors.ink3,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "公式：基础分 × 成长乘区，TRUNC 取整 · 权重常量来自 TableData.FormalInfo[301–308]",
            fontSize = 9.5.sp,
            color = Tc.colors.ink3,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, even: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (even) Tc.colors.surface2.copy(alpha = 0.55f) else Tc.colors.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, color = Tc.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(trimNum(value)) }
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
            color = Tc.colors.ink2,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Tc.colors.surface)
                .border(1.dp, Tc.colors.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { t ->
                    text = t
                    t.toDoubleOrNull()?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontSize = 12.sp, color = Tc.colors.ink),
                cursorBrush = SolidColor(Tc.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
