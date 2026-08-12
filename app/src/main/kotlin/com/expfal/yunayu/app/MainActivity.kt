package com.expfal.yunayu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.expfal.yunayu.ui.screen.welcome.WelcomeScreen
import com.expfal.yunayu.ui.theme.YunayuTheme
import dagger.hilt.android.AndroidEntryPoint

/** Sprint 0 空 Activity：仅 setContent 展示欢迎页。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunayuTheme {
                WelcomeScreen()
            }
        }
    }
}
