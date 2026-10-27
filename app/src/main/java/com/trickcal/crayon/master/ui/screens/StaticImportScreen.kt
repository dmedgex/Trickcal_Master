package com.trickcal.crayon.master.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.capture.StaticScanner
import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.components.BackButton
import com.trickcal.crayon.master.ui.components.TcCard
import com.trickcal.crayon.master.ui.theme.Tc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 静态识图：批量导入相册截图，逐张识别打勾的格子。 */
@Composable
fun StaticImportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var results by remember { mutableStateOf<List<Pair<String, StaticScanner.ImageResult>>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var debugVision by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("选择截图后自动开始识别") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30),
    ) { picked ->
        if (picked.isNullOrEmpty()) return@rememberLauncherForActivityResult
        uris = picked
        results = emptyList()
        status = "已选择 ${picked.size} 张，开始识别…"
        running = true
        scope.launch {
            val scanner = StaticScanner(vm.data.characters.map { it.name })
            val acc = mutableListOf<Pair<String, StaticScanner.ImageResult>>()
            picked.forEachIndexed { index, uri ->
                status = "识别中 ${index + 1}/${picked.size}"
                val bmp = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "image${index + 1}"
                if (bmp == null) {
                    acc.add(name to StaticScanner.ImageResult(name, emptyList(), 0, 0))
                } else {
                    val r = withContext(Dispatchers.Default) { scanner.scan(bmp, name, debugVision) }
                    bmp.recycle()
                    acc.add(name to r)
                }
                results = acc.toList()
            }
            running = false
            val hit = acc.sumOf { it.second.cards.size }
            status = "识别完成：${acc.size} 张，共识别 ${hit} 张卡片"
        }
    }

    // 汇总：角色 → 属性 → 层（每张图用自己识别出的属性）
    val merged = remember(results) {
        val map = mutableMapOf<String, MutableMap<Attr, MutableSet<Int>>>()
        results.forEach { (_, r) ->
            val a = r.attr ?: return@forEach
            r.cards.forEach { card ->
                if (card.layers.isEmpty()) return@forEach
                map.getOrPut(card.name) { mutableMapOf() }
                    .getOrPut(a) { mutableSetOf() }
                    .addAll(card.layers)
            }
        }
        map
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 100.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            BackButton(label = "工具", onClick = onBack)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "静态识图",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = Tc.colors.ink,
            )
            Text(
                text = "批量导入游戏截图，自动读出每个使徒哪几层打了勾。适合先验证识别效果。",
                fontSize = 11.sp,
                color = Tc.colors.ink3,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )

            // 自动识别属性说明
            TcCard(
                padding = 14.dp,
                shape = RoundedCornerShape(18.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("属性自动识别", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                Text(
                    text = "每张图会自动判断属于哪个属性页，无需手动选择；混着导入也可以。",
                    fontSize = 9.5.sp,
                    color = Tc.colors.ink3,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // 选图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Tc.colors.brand)
                    .clickable(enabled = !running) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (running) "识别中…" else "选择截图（可多选）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(status, fontSize = 11.sp, color = Tc.colors.ink2)

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                TcCard(
                    padding = 12.dp,
                    shape = RoundedCornerShape(18.dp),
                    elevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("识别明细", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tc.colors.ink)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (debugVision) "调试视图 开" else "调试视图 关",
                            fontSize = 9.5.sp,
                            color = if (debugVision) Tc.colors.brand else Tc.colors.ink3,
                            modifier = Modifier.clickable { debugVision = !debugVision },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    results.forEach { (name, r) ->
                        val checks = r.cards.sumOf { it.layers.size }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 10.5.sp,
                                    color = Tc.colors.ink,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "${r.attr?.label ?: "属性未识别"} · ${r.rowCount} 行 · " +
                                        "认出 ${r.nameCount} 名 · 有数据 ${r.cards.size} 卡 · 勾 ${checks}",
                                    fontSize = 9.sp,
                                    color = if (r.valid && r.attr != null) Tc.colors.ink3 else Tc.colors.warn,
                                )
                                // 每张卡三格的模板匹配分数，用来排查「为什么这格没判成勾」
                                if (r.cards.isNotEmpty()) {
                                    Text(
                                        text = r.cards.joinToString("  ") { c ->
                                            "${c.name}[" + c.scores.joinToString(" ") { "%.2f".format(it) } + "]"
                                        },
                                        fontSize = 8.sp,
                                        color = Tc.colors.ink3,
                                    )
                                }
                                r.annotated?.let { bmp ->
                                    Spacer(Modifier.height(6.dp))
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                    )
                                }
                            }
                            if (!r.valid) {
                                Text("未识别", fontSize = 9.5.sp, color = Tc.colors.warn)
                            }
                        }
                    }
                }
            }

            if (merged.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                TcCard(
                    padding = 12.dp,
                    shape = RoundedCornerShape(18.dp),
                    elevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val total = merged.values.sumOf { m -> m.values.sumOf { it.size } }
                    Text(
                        text = "汇总：${merged.size} 个角色 · $total 个属性层",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tc.colors.brand,
                    )
                    Spacer(Modifier.height(6.dp))
                    merged.entries.take(40).forEach { (name, attrs) ->
                        val detail = attrs.entries.joinToString("  ") { (a, layers) ->
                            val ls = layers.sorted().joinToString("/")
                            "${a.label} $ls"
                        }
                        Text(
                            text = "$name  $detail",
                            fontSize = 10.sp,
                            color = Tc.colors.ink2,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                    if (merged.size > 40) {
                        Text(
                            text = "… 其余 ${merged.size - 40} 个角色",
                            fontSize = 9.5.sp,
                            color = Tc.colors.ink3,
                        )
                    }
                }
            }
        }

        if (merged.isNotEmpty()) {
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
                        .clickable {
                            val (cells, chars) = vm.applyImportedCells(merged)
                            status = "已写入 $chars 个角色、$cells 个格子"
                            vm.showToast("导入完成：$cells 格")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "写入进度",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** 相册图片解码，长边限制在 2000px 内，避免超大截图占内存。 */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val maxSide = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (maxSide / sample > 2000) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    } catch (_: Throwable) {
        null
    }
}
