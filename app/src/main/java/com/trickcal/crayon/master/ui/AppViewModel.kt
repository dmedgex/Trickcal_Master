package com.trickcal.crayon.master.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.trickcal.crayon.master.data.BackupEntry
import com.trickcal.crayon.master.data.GameData
import com.trickcal.crayon.master.data.GameDataLoader
import com.trickcal.crayon.master.data.SaveSnapshot
import com.trickcal.crayon.master.data.SaveStore
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.domain.BpForm
import com.trickcal.crayon.master.domain.CharacterFilters
import com.trickcal.crayon.master.domain.PdOutcome
import com.trickcal.crayon.master.domain.PetDispatchSolver
import com.trickcal.crayon.master.domain.QuickPaintTarget
import com.trickcal.crayon.master.ui.theme.AppSkin
import org.json.JSONArray
import org.json.JSONObject

enum class BrowseMode(val label: String) { DETAIL("查看详情"), PAINT("蜡笔涂色") }

enum class DisplayMode(val label: String) { DETAIL("详情"), COMPACT("极简") }

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

data class BatchPreview(val charCount: Int, val slotCount: Int, val ids: List<String>)

/** 一键涂色的动作类型（对齐旧版 BatchPaintAction）。 */
enum class BatchAction(
    val label: String,
    val verb: String,
    val confirmLabel: String,
) {
    UNLOCK("批量点亮", "点亮", "确认点亮"),
    LOCK("批量取消", "取消", "确认取消"),
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val data: GameData = GameDataLoader.load(application)
    private val store = SaveStore(application)

    var lit by mutableStateOf<Set<String>>(emptySet())
        private set

    var filters by mutableStateOf(CharacterFilters())

    var browseMode by mutableStateOf(BrowseMode.DETAIL)

    var displayMode by mutableStateOf(DisplayMode.DETAIL)

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var skin by mutableStateOf(AppSkin.CRAYON)
        private set

    /** 首页功能顺序（功能 id 列表），空表示使用默认顺序。 */
    var featureOrder by mutableStateOf<List<String>>(emptyList())
        private set

    var hiddenFeatures by mutableStateOf<Set<String>>(emptySet())
        private set

    var selectedPets by mutableStateOf<Set<Int>>(emptySet())
        private set

    var region by mutableStateOf("")
        private set

    var taskCount by mutableStateOf(3)
        private set

    var overrides by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set

    var pdOutcome by mutableStateOf<PdOutcome?>(null)
        private set

    var bpUid by mutableStateOf(0)
        private set

    var bpForm by mutableStateOf(BpForm())
        private set

    var detailTier by mutableStateOf(1)

    var lastBackup by mutableStateOf(0L)
        private set

    var backups by mutableStateOf<List<BackupEntry>>(emptyList())
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    init {
        val snap = store.load()
        lit = if (snap.initialized) snap.lit else demoProgress()
        selectedPets = snap.selectedPetIds.ifEmpty {
            data.pets.take(14).map { it.id }.toSet()
        }
        region = snap.region?.takeIf { name -> data.regions.any { it.name == name } }
            ?: data.regions.firstOrNull()?.name.orEmpty()
        taskCount = snap.taskCount.coerceIn(1, 5)
        overrides = snap.overrides
        browseMode = if (snap.browseMode == BrowseMode.PAINT.name) BrowseMode.PAINT else BrowseMode.DETAIL
        displayMode = if (snap.displayMode == DisplayMode.COMPACT.name) DisplayMode.COMPACT else DisplayMode.DETAIL
        themeMode = ThemeMode.fromKey(snap.themeMode)
        skin = AppSkin.fromKey(snap.skin)
        featureOrder = snap.featureOrder.split(",").filter { it.isNotBlank() }
        hiddenFeatures = snap.hiddenFeatures.split(",").filter { it.isNotBlank() }.toSet()
        lastBackup = snap.lastBackup
        bpUid = data.defaultBpUid
        backups = store.backups()
    }

    // ---------- 涂色 ----------

    fun toggleSlot(slotId: String) {
        lit = if (lit.contains(slotId)) lit - slotId else lit + slotId
        persist()
    }

    fun applyQuickPaint(chId: String, target: QuickPaintTarget) {
        val mutable = lit.toMutableSet()
        val n = BoardEngine.applyQuickPaint(mutable, target)
        lit = mutable
        persist()
        showToast(if (target.shouldUnlock) "已点亮 $n 格" else "已取消 $n 格")
    }

    // ---------- 列表 ----------

    fun toggleTierFilter(tier: Int) {
        filters = filters.copy(
            tiers = if (filters.tiers.contains(tier)) filters.tiers - tier else filters.tiers + tier,
        )
    }

    fun toggleAttrFilter(attr: com.trickcal.crayon.master.data.Attr) {
        filters = filters.copy(
            attrs = if (filters.attrs.contains(attr)) filters.attrs - attr else filters.attrs + attr,
        )
    }

    fun togglePersonality(p: com.trickcal.crayon.master.data.Personality) {
        filters = filters.copy(personality = if (filters.personality == p) null else p)
    }

    fun setQuery(q: String) {
        filters = filters.copy(query = q)
    }

    fun clearFilters() {
        filters = CharacterFilters()
    }

    /** 一键涂色预览：使用弹窗内独立的层/属性条件，只统计真正会被改动的格子。 */
    fun batchPreview(
        action: BatchAction,
        tiers: Set<Int>,
        attrs: Set<com.trickcal.crayon.master.data.Attr>,
        list: List<com.trickcal.crayon.master.data.Character>,
    ): BatchPreview {
        if (tiers.isEmpty() || attrs.isEmpty()) return BatchPreview(0, 0, emptyList())

        val unlock = action == BatchAction.UNLOCK
        val ids = mutableListOf<String>()
        val chars = HashSet<String>()
        list.forEach { ch ->
            ch.layers.filter { tiers.contains(it.tier) }.forEach { layer ->
                layer.slots.forEach { cell ->
                    val attr = cell.attr
                    val id = cell.slotId
                    if (attr == null || id == null || !attrs.contains(attr)) return@forEach
                    val already = lit.contains(id)
                    if (unlock != already) {
                        ids.add(id)
                        chars.add(ch.id)
                    }
                }
            }
        }
        return BatchPreview(chars.size, ids.size, ids)
    }

    fun applyBatch(
        action: BatchAction,
        tiers: Set<Int>,
        attrs: Set<com.trickcal.crayon.master.data.Attr>,
        list: List<com.trickcal.crayon.master.data.Character>,
    ) {
        val preview = batchPreview(action, tiers, attrs, list)
        if (preview.ids.isEmpty()) {
            showToast("当前条件下没有可执行的格子")
            return
        }
        val mutable = lit.toMutableSet()
        val unlock = action == BatchAction.UNLOCK
        preview.ids.forEach { if (unlock) mutable.add(it) else mutable.remove(it) }
        lit = mutable
        persist()
        showToast("已${action.verb} ${preview.ids.size} 格")
    }

    fun changeBrowseMode(mode: BrowseMode) {
        browseMode = mode
        persist()
    }

    fun changeDisplayMode(mode: DisplayMode) {
        displayMode = mode
        persist()
    }

    fun changeThemeMode(mode: ThemeMode) {
        themeMode = mode
        persist()
    }

    fun changeSkin(next: AppSkin) {
        skin = next
        persist()
    }

    /** 按当前顺序返回功能 id 列表，未自定义时返回默认顺序。 */
    fun resolvedFeatureOrder(defaultOrder: List<String>): List<String> {
        if (featureOrder.isEmpty()) return defaultOrder
        val known = defaultOrder.toSet()
        val kept = featureOrder.filter { known.contains(it) }
        return kept + defaultOrder.filterNot { kept.contains(it) }
    }

    fun moveFeature(id: String, defaultOrder: List<String>, delta: Int) {
        val list = resolvedFeatureOrder(defaultOrder).toMutableList()
        val i = list.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        featureOrder = list
        persist()
    }

    /** 识图导入结果写入：按角色名 + 属性 + 层，点亮其全部对应格子。返回 (新增格子数, 命中角色数)。 */
    fun applyImportedCells(imported: Map<String, Map<com.trickcal.crayon.master.data.Attr, Set<Int>>>): Pair<Int, Int> {
        val mutable = lit.toMutableSet()
        var added = 0
        var hitChars = 0
        data.characters.forEach { ch ->
            val byAttr = imported[ch.name] ?: return@forEach
            var touched = false
            ch.layers.forEach { layer ->
                val tiers = layer.slots
                    .mapNotNull { it.attr }
                    .distinct()
                    .filter { attr -> byAttr[attr]?.contains(layer.tier) == true }
                if (tiers.isEmpty()) return@forEach
                layer.slots.forEach { cell ->
                    val attr = cell.attr ?: return@forEach
                    val id = cell.slotId ?: return@forEach
                    if (byAttr[attr]?.contains(layer.tier) == true && mutable.add(id)) {
                        added++
                        touched = true
                    }
                }
            }
            if (touched) hitChars++
        }
        lit = mutable
        persist()
        return added to hitChars
    }

    fun toggleFeatureVisible(id: String) {        hiddenFeatures = if (hiddenFeatures.contains(id)) {
            hiddenFeatures - id
        } else {
            if (hiddenFeatures.size >= 6) {
                showToast("至少保留一个功能")
                return
            }
            hiddenFeatures + id
        }
        persist()
    }

    fun resetFeatures() {
        featureOrder = emptyList()
        hiddenFeatures = emptySet()
        persist()
        showToast("已恢复默认布局")
    }

    // ---------- 宠物派遣 ----------

    fun selectRegion(name: String) {
        region = name
        pdOutcome = null
        persist()
    }

    fun selectTaskCount(n: Int) {
        taskCount = n
        pdOutcome = null
        persist()
    }

    fun togglePet(id: Int) {
        selectedPets = if (selectedPets.contains(id)) selectedPets - id else selectedPets + id
        persist()
    }

    fun setAllPets(on: Boolean) {
        selectedPets = if (on) data.pets.map { it.id }.toSet() else emptySet()
        persist()
    }

    fun setTaskOverride(taskKey: String, skills: List<String>?) {
        val mutable = overrides.toMutableMap()
        if (skills.isNullOrEmpty()) mutable.remove(taskKey) else mutable[taskKey] = skills
        overrides = mutable
        pdOutcome = null
        persist()
    }

    fun resetRegionOverrides() {
        val current = data.regions.firstOrNull { it.name == region } ?: return
        val mutable = overrides.toMutableMap()
        current.tasks.forEach { mutable.remove(taskKey(region, it.id)) }
        overrides = mutable
        pdOutcome = null
        persist()
        showToast("已恢复本区域全部默认特性")
    }

    /** 行内切换某个任务的特性：点已选取消、点未选追加，最多 2 个；与默认一致时自动回到默认。 */
    fun toggleTaskSkill(taskId: Int, skill: String) {
        val current = effectiveBonus(taskId)
        val next = if (current.contains(skill)) {
            current - skill
        } else {
            if (current.size >= 2) {
                showToast("每个任务最多 2 个特性")
                return
            }
            current + skill
        }
        val default = data.regions.firstOrNull { it.name == region }
            ?.tasks?.firstOrNull { it.id == taskId }?.bonusSkills.orEmpty()
        val sameAsDefault = next.isEmpty() || next.toSet() == default.toSet()
        setTaskOverride(taskKey(region, taskId), if (sameAsDefault) null else next)
    }

    fun resetTaskOverride(taskId: Int) {
        setTaskOverride(taskKey(region, taskId), null)
    }

    fun effectiveBonus(taskId: Int): List<String> {
        val base = data.regions.firstOrNull { it.name == region }
            ?.tasks?.firstOrNull { it.id == taskId }?.bonusSkills.orEmpty()
        return overrides[taskKey(region, taskId)] ?: base
    }

    fun overrideCount(): Int {
        val current = data.regions.firstOrNull { it.name == region } ?: return 0
        return current.tasks.count { overrides.containsKey(taskKey(region, it.id)) }
    }

    fun runPetDispatch() {
        val base = data.regions.firstOrNull { it.name == region } ?: return
        if (selectedPets.isEmpty()) {
            showToast("请先选择宠物")
            return
        }
        val withOverrides = base.copy(
            tasks = base.tasks.map { it.copy(bonusSkills = effectiveBonus(it.id)) },
        )
        val owned = data.pets.filter { selectedPets.contains(it.id) }
        val outcome = PetDispatchSolver.solve(withOverrides, taskCount, owned)
        pdOutcome = outcome
        if (outcome.ok) showToast("求解完成 · ${outcome.elapsedMs}ms")
    }

    // ---------- 战斗力 ----------

    fun selectBpUid(uid: Int) {
        bpUid = uid
    }

    fun updateBpForm(form: BpForm) {
        bpForm = form
    }

    // ---------- 存档 ----------

    fun snapshot(): SaveSnapshot = SaveSnapshot(
        lit = lit,
        selectedPetIds = selectedPets,
        region = region,
        taskCount = taskCount,
        overrides = overrides,
        browseMode = browseMode.name,
        displayMode = displayMode.name,
        themeMode = themeMode.name,
        skin = skin.name,
        featureOrder = featureOrder.joinToString(","),
        hiddenFeatures = hiddenFeatures.joinToString(","),
        lastBackup = lastBackup,
        initialized = true,
    )

    private fun persist() {
        store.save(snapshot())
    }

    fun createBackup() {
        val snap = snapshot().copy(lastBackup = System.currentTimeMillis())
        lastBackup = snap.lastBackup
        backups = store.addBackup(snap)
        store.save(snap)
        showToast("已创建快照")
    }

    fun restoreBackup(entry: BackupEntry) {
        val snap = store.restore(entry) ?: run {
            showToast("快照无法读取")
            return
        }
        applySnapshot(snap)
        showToast("已回滚到该快照")
    }

    fun clearProgress() {
        lit = emptySet()
        persist()
        showToast("已清空涂色进度")
    }

    fun loadDemoProgress() {
        lit = demoProgress()
        persist()
        showToast("已载入示例进度")
    }

    fun exportJson(): String {
        val litArr = JSONArray()
        lit.forEach { litArr.put(it) }
        val overridesObj = JSONObject()
        overrides.forEach { (k, v) ->
            overridesObj.put(k, JSONArray().also { arr -> v.forEach { arr.put(it) } })
        }
        val root = JSONObject()
            .put("format", "trickcal-save")
            .put("schemaVersion", 1)
            .put("appVersion", VERSION_NAME)
            .put("savedAt", System.currentTimeMillis())
            .put(
                "modules",
                JSONObject()
                    .put(
                        "crayon",
                        JSONObject().put("version", 1).put("data", JSONObject().put("litSlotIds", litArr)),
                    )
                    .put(
                        "petDispatch",
                        JSONObject().put("version", 1).put(
                            "data",
                            JSONObject()
                                .put("region", region)
                                .put("taskCount", taskCount)
                                .put("taskBonusSkillOverrides", overridesObj),
                        ),
                    ),
            )
        return root.toString(2)
    }

    /** 兼容新格式与旧版 litSlotIds 数组。 */
    fun importJson(text: String): Boolean {
        return runCatching {
            val root = JSONObject(text)
            val modules = root.optJSONObject("modules")
            val crayon = modules?.optJSONObject("crayon")?.optJSONObject("data")
            val litArr = crayon?.optJSONArray("litSlotIds") ?: root.optJSONArray("litSlotIds")
                ?: return@runCatching false
            val incoming = HashSet<String>()
            for (i in 0 until litArr.length()) incoming.add(litArr.getString(i))
            val valid = incoming.filter { data.slotById.containsKey(it) }.toSet()
            lit = valid

            val pd = modules?.optJSONObject("petDispatch")?.optJSONObject("data")
            pd?.optString("region")?.takeIf { it.isNotBlank() }?.let { name ->
                if (data.regions.any { it.name == name }) region = name
            }
            pd?.optInt("taskCount", taskCount)?.let { taskCount = it.coerceIn(1, 5) }
            pd?.optJSONObject("taskBonusSkillOverrides")?.let { obj ->
                val map = HashMap<String, List<String>>()
                obj.keys().forEach { key ->
                    val arr = obj.optJSONArray(key) ?: return@forEach
                    val list = ArrayList<String>()
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                    map[key] = list
                }
                overrides = map
            }
            persist()
            true
        }.getOrDefault(false)
    }

    private fun applySnapshot(snap: SaveSnapshot) {
        lit = snap.lit
        selectedPets = snap.selectedPetIds
        region = snap.region ?: region
        taskCount = snap.taskCount
        overrides = snap.overrides
        store.save(snap.copy(initialized = true))
    }

    fun showToast(message: String) {
        toast = message
    }

    fun consumeToast() {
        toast = null
    }

    private fun demoProgress(): Set<String> = runCatching {
        val text = getApplication<Application>().assets
            .open("data/demo_progress.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("lit")
        val out = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        out.retainAll { data.slotById.containsKey(it) }
        out
    }.getOrElse { emptySet() }

    private fun taskKey(regionName: String, taskId: Int): String = "$regionName#$taskId"

    companion object {
        /** 版本号唯一来源，与 app/build.gradle.kts 的 versionName 保持一致。 */
        const val VERSION_NAME = "2.0.0-beta"
    }
}
