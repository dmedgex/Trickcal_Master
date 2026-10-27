package com.trickcal.crayon.master.domain

import com.trickcal.crayon.master.data.BpCharacter

/** 战斗力输入面板，默认值与 demo 一致（对齐 Excel 样例，输出 762,234）。 */
data class BpForm(
    val attackPhysic: Double = 53080.0,
    val attackMagic: Double = 4262.0,
    val defensePhysic: Double = 57104.0,
    val defenseMagic: Double = 57179.0,
    val hp: Double = 553870.0,
    val criticalRate: Double = 23099.0,
    val criticalMult: Double = 22942.0,
    val criticalResist: Double = 21198.0,
    val criticalMultResist: Double = 21207.0,
    val attackSpeed: Double = 100.0,
    val spLevel: Double = 12.0,
    val ultLevel: Double = 7.0,
    val passiveLevel: Double = 7.0,
    val asideGrade: Double = 2.0,
)

data class BpResult(
    val mainAttack: Double,
    val defenseSum: Double,
    val asideBonus: Double,
    val baseScore: Double,
    val growth: Double,
    val power: Double,
)

/** 对齐 BattlePowerCalculatorEngine.kt / core.js 的 calcBattlePower。 */
object BattlePowerEngine {

    private const val W_ATTACK = 2.1
    private const val W_DEFENSE = 0.7
    private const val W_HP = 0.08
    private const val W_CRIT_RATE = 0.7
    private const val W_CRIT_MULT = 0.7
    private const val W_CRIT_RESIST = 0.7
    private const val W_CRIT_MULT_RESIST = 0.7
    private const val W_ATK_SPEED = 0.6

    fun calc(profile: BpCharacter, f: BpForm): BpResult {
        val mainAttack = if (profile.attackType == "物理") f.attackPhysic else f.attackMagic
        val defenseSum = f.defensePhysic + f.defenseMagic
        val asideBonus = if (f.asideGrade >= 2) profile.asideValueA else 0.0

        val baseScore =
            mainAttack * W_ATTACK +
                defenseSum * W_DEFENSE +
                f.hp * W_HP +
                f.criticalRate * W_CRIT_RATE +
                f.criticalMult * W_CRIT_MULT +
                f.criticalResist * W_CRIT_RESIST +
                f.criticalMultResist * W_CRIT_MULT_RESIST +
                f.attackSpeed * W_ATK_SPEED

        val growth = 1.0 +
            profile.activeSkillValueA * f.spLevel +
            profile.ultimateSkillValueA * f.ultLevel +
            profile.passiveValueA * f.passiveLevel +
            profile.weightValueA +
            asideBonus

        return BpResult(
            mainAttack = mainAttack,
            defenseSum = defenseSum,
            asideBonus = asideBonus,
            baseScore = baseScore,
            growth = growth,
            power = Math.floor(baseScore * growth),
        )
    }
}
