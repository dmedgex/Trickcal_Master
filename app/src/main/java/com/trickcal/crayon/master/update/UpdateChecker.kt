package com.trickcal.crayon.master.update

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream

/** 检查结果。失败也要区分开，否则「网络不通」会被误报成「已是最新」。 */
sealed interface UpdateResult {
    data class Available(val info: UpdateInfo, val force: Boolean) : UpdateResult
    data object Latest : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * 版本清单拉取器。
 *
 * 只有一条链路：读仓库里的静态 JSON，不碰 GitHub API。原因是未认证的
 * `api.github.com` 限流 60 次/小时/IP，对「每个用户点一次」的量来说不算低，
 * 而静态文件没有配额问题。
 *
 * 调用方必须放在 IO 线程：这里做的是阻塞式网络 IO。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val TIMEOUT_MS = 5000

    /**
     * 按顺位尝试的地址。
     *
     * 1. raw 无 CDN 缓存，改完清单立刻生效；
     * 2. jsDelivr 作为备用，`@main` 最长约 12 小时缓存，发版后要手动 purge：
     *    https://purge.jsdelivr.net/gh/dmedgex/Trickcal_Master@main/update/version.json
     */
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/dmedgex/Trickcal_Master/main/update/version.json",
        "https://cdn.jsdelivr.net/gh/dmedgex/Trickcal_Master@main/update/version.json",
    )

    /** 清单不存在。单独一个类型，避免和网络故障混在一起。 */
    private class NotFoundException : IOException("version.json 不存在")

    fun check(currentVersionCode: Int): UpdateResult {
        val errors = LinkedHashSet<String>()
        for (url in SOURCES) {
            // 每个地址先按系统代理（默认行为）试一次；确实配了代理时再直连补一次。
            // 国内常见组合是「代理开着、但代理本身到不了 raw」——此时直连通、代理不通，
            // 只按默认行为走一次就会误判成网络不可用。
            val modes = if (hasSystemProxy(url)) listOf(true, false) else listOf(true)
            for (useSystemProxy in modes) {
                val text = try {
                    fetch(url, useSystemProxy)
                } catch (e: NotFoundException) {
                    // 清单没发布是确定结论，换地址或换通道都没意义，直接返回。
                    Log.w(TAG, "manifest not found: $url")
                    return UpdateResult.Failed("版本清单还没发布")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "fetch failed [${if (useSystemProxy) "proxy" else "direct"}] $url: " +
                            "${e.javaClass.simpleName} ${e.message}",
                    )
                    errors.add(describe(e))
                    continue
                }
                val info = UpdateManifest.parse(text)
                if (info == null) {
                    errors.add("清单格式不正确")
                    continue
                }
                return if (info.versionCode > currentVersionCode) {
                    UpdateResult.Available(info, info.isForce(currentVersionCode))
                } else {
                    UpdateResult.Latest
                }
            }
        }
        return UpdateResult.Failed(summarize(errors))
    }

    /** 系统是否为本地址配了代理；没配就没必要再重复试一遍直连。 */
    private fun hasSystemProxy(rawUrl: String): Boolean = runCatching {
        ProxySelector.getDefault()?.select(URI(rawUrl))
            ?.any { it.type() != Proxy.Type.DIRECT } == true
    }.getOrDefault(false)

    private fun fetch(rawUrl: String, useSystemProxy: Boolean): String {
        val url = URL(rawUrl)
        val connection = if (useSystemProxy) {
            url.openConnection()
        } else {
            // NO_PROXY 表示显式绕过系统代理。
            url.openConnection(Proxy.NO_PROXY)
        }
        val conn = connection as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            // GitHub 会拒绝默认的 "Java/<ver>" UA，不显式设置可能直接 403。
            setRequestProperty("User-Agent", "TrickcalMaster-Android")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            val code = conn.responseCode
            // 404 是「清单还没发布」而不是网络故障，单独给文案，否则用户以为是自己网络问题。
            if (code == 404) throw NotFoundException()
            if (code !in 200..299) throw IOException("HTTP $code")
            // raw 返回的 Content-Type 是 text/plain，不能靠类型判断，直接读流。
            val stream = if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 把异常翻成用户能读懂、且能据此行动的中文。 */
    private fun describe(e: Exception): String = when (e) {
        is SocketTimeoutException -> "连接超时"
        is UnknownHostException -> "域名无法解析"
        is SocketException -> "连接被重置（被网络或代理拦截）"
        is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "网络异常"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** 多个地址、两条通道的错误合并展示，最多两条，避免 toast 过长。 */
    private fun summarize(errors: Set<String>): String {
        if (errors.isEmpty()) return "网络不可用"
        return errors.take(2).joinToString("；")
    }
}
