package com.trickcal.crayon.master.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一份可持久化的应用状态。 */
data class SaveSnapshot(
    val lit: Set<String> = emptySet(),
    val selectedPetIds: Set<Int> = emptySet(),
    val region: String? = null,
    val taskCount: Int = 3,
    val overrides: Map<String, List<String>> = emptyMap(),
    val browseMode: String = "DETAIL",
    val displayMode: String = "DETAIL",
    val themeMode: String = "SYSTEM",
    val skin: String = "CRAYON",
    val featureOrder: String = "",
    val hiddenFeatures: String = "",
    val lastBackup: Long = 0L,
    val initialized: Boolean = false,
)

data class BackupEntry(
    val at: Long,
    val litCount: Int,
    val payload: String,
)

/** 本地存档：SharedPreferences + JSON，可容纳全部模块状态与自动快照。 */
class SaveStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trickcal_master_save", Context.MODE_PRIVATE)

    fun load(): SaveSnapshot {
        val raw = prefs.getString(KEY_STATE, null) ?: return SaveSnapshot()
        return runCatching { decode(JSONObject(raw)) }.getOrElse { SaveSnapshot() }
    }

    fun save(state: SaveSnapshot) {
        prefs.edit().putString(KEY_STATE, encode(state).toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun backups(): List<BackupEntry> {
        val raw = prefs.getString(KEY_BACKUPS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BackupEntry(
                    at = o.getLong("at"),
                    litCount = o.getInt("litCount"),
                    payload = o.getString("payload"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun addBackup(state: SaveSnapshot, max: Int = 10): List<BackupEntry> {
        val list = backups().toMutableList()
        list.add(0, BackupEntry(System.currentTimeMillis(), state.lit.size, encode(state).toString()))
        val trimmed = list.take(max)
        val arr = JSONArray()
        trimmed.forEach { e ->
            arr.put(
                JSONObject()
                    .put("at", e.at)
                    .put("litCount", e.litCount)
                    .put("payload", e.payload),
            )
        }
        prefs.edit().putString(KEY_BACKUPS, arr.toString()).apply()
        return trimmed
    }

    fun restore(entry: BackupEntry): SaveSnapshot? =
        runCatching { decode(JSONObject(entry.payload)) }.getOrNull()

    private fun encode(s: SaveSnapshot): JSONObject {
        val litArr = JSONArray()
        s.lit.forEach { litArr.put(it) }

        val petsArr = JSONArray()
        s.selectedPetIds.forEach { petsArr.put(it) }

        val overridesObj = JSONObject()
        s.overrides.forEach { (k, v) ->
            overridesObj.put(k, JSONArray().also { arr -> v.forEach { arr.put(it) } })
        }

        return JSONObject()
            .put("lit", litArr)
            .put("pets", petsArr)
            .put("region", s.region ?: JSONObject.NULL)
            .put("taskCount", s.taskCount)
            .put("overrides", overridesObj)
            .put("browseMode", s.browseMode)
            .put("displayMode", s.displayMode)
            .put("themeMode", s.themeMode)
            .put("skin", s.skin)
            .put("featureOrder", s.featureOrder)
            .put("hiddenFeatures", s.hiddenFeatures)
            .put("lastBackup", s.lastBackup)
            .put("initialized", s.initialized)
    }

    private fun decode(o: JSONObject): SaveSnapshot {
        val lit = HashSet<String>()
        o.optJSONArray("lit")?.let { arr ->
            for (i in 0 until arr.length()) lit.add(arr.getString(i))
        }

        val pets = HashSet<Int>()
        o.optJSONArray("pets")?.let { arr ->
            for (i in 0 until arr.length()) pets.add(arr.getInt(i))
        }

        val overrides = HashMap<String, List<String>>()
        o.optJSONObject("overrides")?.let { obj ->
            obj.keys().forEach { key ->
                val arr = obj.optJSONArray(key) ?: return@forEach
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                overrides[key] = list
            }
        }

        val regionJson = o.opt("region")

        return SaveSnapshot(
            lit = lit,
            selectedPetIds = pets,
            region = if (regionJson == null || regionJson == JSONObject.NULL) null else regionJson.toString(),
            taskCount = o.optInt("taskCount", 3),
            overrides = overrides,
            browseMode = o.optString("browseMode", "DETAIL"),
            displayMode = o.optString("displayMode", "DETAIL"),
            themeMode = o.optString("themeMode", "SYSTEM"),
            skin = o.optString("skin", "CRAYON"),
            featureOrder = o.optString("featureOrder", ""),
            hiddenFeatures = o.optString("hiddenFeatures", ""),
            lastBackup = o.optLong("lastBackup", 0L),
            initialized = o.optBoolean("initialized", false),
        )
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_BACKUPS = "backups"
    }
}
