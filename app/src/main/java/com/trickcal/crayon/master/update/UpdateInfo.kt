package com.trickcal.crayon.master.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * 更新清单，对应仓库根目录的 `update/version.json`。
 *
 * 判新只用 [versionCode] 整型。绝不能拿 [versionName] 做字符串比较：
 * "2.0.0-beta" 与 "2.0.10" 按字典序会把前者判成更新，带后缀时更是必错。
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val changelog: List<String>,
    val downloadUrl: String,
    val downloadCode: String,
    val mirrorUrl: String,
    val publishedAt: String,
) {
    /** 当前版本低于最低支持版本即为强制更新；不额外设 force 字段，避免两处口径打架。 */
    fun isForce(currentVersionCode: Int): Boolean =
        minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode

    /** 实际可跳转的地址：主链优先，主链为空时才退到镜像。 */
    val effectiveUrl: String
        get() = downloadUrl.ifBlank { mirrorUrl }
}

/** 清单解析。任何字段异常都返回 null，由上层当成「本次没查到」处理，不抛给 UI。 */
object UpdateManifest {

    fun parse(text: String): UpdateInfo? = runCatching {
        val obj = JSONObject(text)
        val code = obj.optInt("versionCode", 0)
        if (code <= 0) return null
        UpdateInfo(
            versionCode = code,
            versionName = obj.optString("versionName").ifBlank { "新版本" },
            minSupportedVersionCode = obj.optInt("minSupportedVersionCode", 0),
            changelog = obj.optJSONArray("changelog").toStringList(),
            downloadUrl = obj.optString("downloadUrl").trim(),
            downloadCode = obj.optString("downloadCode").trim(),
            mirrorUrl = obj.optString("mirrorUrl").trim(),
            publishedAt = obj.optString("publishedAt").trim(),
        )
    }.getOrNull()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val line = optString(i).trim()
            if (line.isNotEmpty()) out.add(line)
        }
        return out
    }
}
