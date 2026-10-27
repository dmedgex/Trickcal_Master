package com.trickcal.crayon.master

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trickcal.crayon.master.ui.AppRoot
import com.trickcal.crayon.master.ui.AppViewModel
import com.trickcal.crayon.master.ui.ThemeMode
import com.trickcal.crayon.master.ui.theme.TrickcalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 初始化 OpenCV 并加载符号模板（assets/glyph）。detect() 依赖这一步，
        // 漏调用会直接返回空列表 —— 之前「菱形=0」就是这个原因。
        com.trickcal.crayon.master.capture.BoardScanner.init(this)
        setContent {
            val vm: AppViewModel = viewModel()
            val systemDark = isSystemInDarkTheme()
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TrickcalTheme(skin = vm.skin, darkTheme = dark) {
                AppRoot(vm)
            }
        }
    }
}
