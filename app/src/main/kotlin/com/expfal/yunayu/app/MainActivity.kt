package com.expfal.yunayu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.expfal.yunayu.ui.screen.home.HomeScreen
import com.expfal.yunayu.ui.theme.YunayuTheme
import dagger.hilt.android.AndroidEntryPoint

/** 应用入口：setContent 展示首页（含「3秒极速记账」快捷入口）。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunayuTheme {
                HomeScreen()
            }
        }
    }
}
