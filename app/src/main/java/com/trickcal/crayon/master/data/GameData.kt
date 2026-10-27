package com.trickcal.crayon.master.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 全量静态数据。首次解析后常驻内存。 */
class GameData(
    val characters: List<Character>,
    val pets: List<Pet>,
    val regions: List<Region>,
    val bpCharacters: List<BpCharacter>,
    val defaultBpUid: Int,
) {
    val allSlots: List<Cell> by lazy {
        characters.flatMap { ch -> ch.layers.flatMap { it.slots } }
    }

    val slotById: Map<String, Cell> by lazy {
        allSlots.mapNotNull { c -> c.slotId?.let { id -> id to c } }.toMap()
    }

    val characterById: Map<String, Character> by lazy { characters.associateBy { it.id } }

    val petById: Map<Int, Pet> by lazy { pets.associateBy { it.id } }

    val totalTasks: Int get() = regions.sumOf { it.tasks.size }
}

object GameDataLoader {

    fun load(context: Context): GameData {
        val characters = parseCharacters(readAsset(context, "data/characters.json"))
        val pets = parsePets(readAsset(context, "data/petdispatch.json"))
        val regions = parseRegions(readAsset(context, "data/petdispatch.json"))
        val bp = parseBattlePower(readAsset(context, "data/battlepower.json"))
        return GameData(
            characters = characters,
            pets = pets,
            regions = regions,
            bpCharacters = bp.first,
            defaultBpUid = bp.second,
        )
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun parseCharacters(json: String): List<Character> {
        val arr = JSONObject(json).getJSONArray("characters")
        val out = ArrayList<Character>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val layersJson = o.getJSONArray("layers")
            val layers = ArrayList<Layer>(layersJson.length())
            for (j in 0 until layersJson.length()) {
                val lo = layersJson.getJSONObject(j)
                val tier = lo.getInt("tier")
                val rowsJson = lo.getJSONArray("rows")
                val rows = ArrayList<String>(rowsJson.length())
                for (k in 0 until rowsJson.length()) rows.add(rowsJson.getString(k))
                val width = rows.maxOfOrNull { it.length } ?: 0
                layers.add(Layer(charId = id, tier = tier, rows = rows, width = width))
            }
            layers.sortBy { it.tier }
            out.add(
                Character(
                    id = id,
                    name = o.getString("name"),
                    avatarKey = o.getString("avatarKey"),
                    personality = Personality.fromKey(o.optString("personality")),
                    sortOrder = o.optInt("sortOrder", i),
                    layers = layers,
                ),
            )
        }
        out.sortBy { it.sortOrder }
        return out
    }

    private fun parsePets(json: String): List<Pet> {
        val arr = JSONObject(json).getJSONArray("pets")
        val out = ArrayList<Pet>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val skillsJson = o.getJSONArray("skills")
            val skills = ArrayList<PetSkill>(skillsJson.length())
            for (j in 0 until skillsJson.length()) {
                val so = skillsJson.getJSONObject(j)
                skills.add(
                    PetSkill(
                        name = so.getString("name"),
                        level = so.optString("level"),
                        score = so.optInt("score"),
                    ),
                )
            }
            out.add(
                Pet(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    rarity = o.optString("rarity", "COMMON"),
                    baseScore = o.optInt("baseScore"),
                    skills = skills,
                    imageAssetName = o.optString("imageAssetName"),
                ),
            )
        }
        out.sortBy { it.id }
        return out
    }

    private fun parseRegions(json: String): List<Region> {
        val arr = JSONObject(json).getJSONArray("regions")
        val out = ArrayList<Region>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Region(name = o.getString("name"), tasks = parseTasks(o.getJSONArray("tasks"))))
        }
        return out
    }

    private fun parseTasks(arr: JSONArray): List<PdTask> {
        val out = ArrayList<PdTask>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val skillsJson = o.optJSONArray("bonusSkills")
            val skills = ArrayList<String>()
            if (skillsJson != null) {
                for (j in 0 until skillsJson.length()) skills.add(skillsJson.getString(j))
            }
            out.add(
                PdTask(
                    id = o.getInt("id"),
                    area = o.optString("area"),
                    task = o.optString("task"),
                    bonusSkills = skills,
                ),
            )
        }
        out.sortBy { it.id }
        return out
    }

    private fun parseBattlePower(json: String): Pair<List<BpCharacter>, Int> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("characters")
        val out = ArrayList<BpCharacter>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                BpCharacter(
                    uid = o.getInt("uid"),
                    name = o.optString("name"),
                    resourceName = o.optString("resourceName"),
                    attackType = o.optString("attackType", "物理"),
                    nameKey = o.optString("nameKey"),
                    activeSkillValueA = o.optString("activeSkillValueA").toDoubleOrZero(),
                    ultimateSkillValueA = o.optString("ultimateSkillValueA").toDoubleOrZero(),
                    passiveValueA = o.optString("passiveValueA").toDoubleOrZero(),
                    weightValueA = o.optString("weightValueA").toDoubleOrZero(),
                    asideValueA = o.optString("asideValueA").toDoubleOrZero(),
                ),
            )
        }
        return out to root.optInt("defaultCharacterUid", out.firstOrNull()?.uid ?: 0)
    }

    private fun String.toDoubleOrZero(): Double = trim().toDoubleOrNull() ?: 0.0
}
