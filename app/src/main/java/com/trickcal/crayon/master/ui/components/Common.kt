package com.trickcal.crayon.master.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trickcal.crayon.master.ui.theme.Tc

/** assets 图片加载：LruCache 缓存 ImageBitmap，避免列表滚动反复解码。 */
object AssetImages {
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    fun load(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bitmap = runCatching {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
        if (bitmap != null) cache.put(path, bitmap)
        return bitmap
    }
}

@Composable
fun AssetImage(
    path: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val bitmap = remember(path) { AssetImages.load(context, path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha,
        )
    } else {
        Box(modifier = modifier.background(Tc.colors.surface2))
    }
}

/** 未涂状态的灰度滤镜（对应 demo 的 grayscale + brightness）。 */
fun dimFilter(strength: Float = 0.06f): androidx.compose.ui.graphics.ColorFilter =
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(strength) },
    )

/**
 * 未拥有/未涂状态的「变暗」滤镜：整体压低亮度、保留原有配色，
 * 对比度仍在，不会像去色那样发灰发白。
 */
fun darkenFilter(scale: Float = 0.62f): androidx.compose.ui.graphics.ColorFilter =
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(scale, scale, scale, 1f) },
    )

@Composable
fun TcCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: Dp = 14.dp,
    background: androidx.compose.ui.graphics.Brush? = null,
    elevation: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .then(if (Tc.colors.isDark) Modifier else Modifier.shadow(elevation, shape, clip = false))
        .clip(shape)
        .then(
            if (background != null) {
                Modifier.background(background)
            } else {
                Modifier.background(Tc.colors.surface)
            },
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(padding)
    Column(modifier = base, content = content)
}

/** 复刻 demo `.ringcard` / `.hero2` 的「品牌色 → 表面色」对角渐变。 */
@Composable
fun brandGradient(): androidx.compose.ui.graphics.Brush {
    val mix = if (Tc.colors.isDark) 0.24f else 0.13f
    val start = androidx.compose.ui.graphics.lerp(Tc.colors.surface, Tc.colors.brand, mix)
    return androidx.compose.ui.graphics.Brush.linearGradient(
        0f to start,
        0.62f to Tc.colors.surface,
        1f to Tc.colors.surface,
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Tc.colors.ink,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** 对齐 demo `.seg` / `.seg-sm`：底槽 34dp、内块 28dp、选中白底深色字。 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tc.colors.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Tc.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Tc.colors.ink else Tc.colors.ink2,
                )
            }
        }
    }
}

@Composable
fun TagPill(
    text: String,
    color: androidx.compose.ui.graphics.Color = Tc.colors.ink3,
    background: androidx.compose.ui.graphics.Color = Tc.colors.surface2,
    fontSize: Int = 10,
) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** 独立功能页返回按钮：白底胶囊 + 描边阴影，任意背景下都能看清、可点区域 36dp。 */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .then(if (Tc.colors.isDark) Modifier else Modifier.shadow(2.dp, shape, clip = false))
            .clip(shape)
            .background(Tc.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Tc.colors.brand,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tc.colors.ink2,
        )
    }
}

/** 可滚动的标准页面外壳（状态栏留白 + 16dp 边距）。 */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 24.dp),
        content = content,
    )
}

/** 对齐 demo `.search`：41dp 高、圆角 13、白底、左图标、13.5sp 输入。 */
@Composable
fun SearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(41.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Tc.colors.surface)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Tc.colors.ink3,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 13.5.sp, color = Tc.colors.ink3)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.5.sp,
                    color = Tc.colors.ink,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Tc.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
