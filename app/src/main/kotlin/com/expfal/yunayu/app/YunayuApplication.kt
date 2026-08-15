package com.expfal.yunayu.app

import android.app.Application
import android.util.Log
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.repository.TagRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Hilt 入口。启动时触发一次 Room 建表 + 查询，验证数据库真实初始化。 */
@HiltAndroidApp
class YunayuApplication : Application() {

    @Inject
    lateinit var tagRepository: TagRepository

    @Inject
    lateinit var ensureReportsUseCase: EnsureReportsUseCase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        verifyDatabase()
        ensureReports()
    }

    /** 查询种子化根标签，经 Logcat（tag: YunayuDB）确认 Room 初始化成功。 */
    private fun verifyDatabase() {
        applicationScope.launch {
            runCatching {
                val roots = tagRepository.getChildren(parentId = null)
                Log.i(TAG, "Room 初始化成功，根标签(${roots.size}): " + roots.joinToString { it.name })
            }.onFailure { error ->
                Log.e(TAG, "Room 初始化失败", error)
            }
        }
    }

    /** 启动时补生成上月 / 上年报告（独立协程，不阻塞建库校验，失败仅记日志）。 */
    private fun ensureReports() {
        applicationScope.launch {
            runCatching { ensureReportsUseCase.ensure(LocalDate.now()) }
                .onFailure { Log.e(REPORT_TAG, "报告补生成失败", it) }
        }
    }

    companion object {
        private const val TAG = "YunayuDB"
        private const val REPORT_TAG = "YunayuReport"
    }
}
