package com.expfal.yunayu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.expfal.yunayu.ui.screen.home.EXTRA_OPEN_WEEKLY_REPORT
import com.expfal.yunayu.ui.screen.home.HomeScreen
import com.expfal.yunayu.ui.theme.YunayuTheme
import dagger.hilt.android.AndroidEntryPoint

/** 应用入口：setContent 展示首页（含「3秒极速记账」快捷入口）。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openWeeklyReport by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        openWeeklyReport = intent.consumeOpenWeeklyReport()
        enableEdgeToEdge()
        setContent {
            YunayuTheme {
                HomeScreen(
                    openWeeklyReport = openWeeklyReport,
                    onOpenWeeklyReportConsumed = { openWeeklyReport = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.consumeOpenWeeklyReport()) {
            openWeeklyReport = true
        }
    }

    private fun Intent?.consumeOpenWeeklyReport(): Boolean {
        if (this == null || !getBooleanExtra(EXTRA_OPEN_WEEKLY_REPORT, false)) return false
        removeExtra(EXTRA_OPEN_WEEKLY_REPORT)
        return true
    }
}
