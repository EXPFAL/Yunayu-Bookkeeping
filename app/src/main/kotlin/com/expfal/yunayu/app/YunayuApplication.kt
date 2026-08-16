package com.expfal.yunayu.app

import android.app.Application
import android.util.Log
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.EnsureAccountsUseCase
import com.expfal.yunayu.domain.usecase.EnsureIncomeTagsUseCase
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

    @Inject
    lateinit var ensureIncomeTagsUseCase: EnsureIncomeTagsUseCase

    @Inject
    lateinit var ensureAccountsUseCase: EnsureAccountsUseCase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        verifyDatabase()
        ensureReports()
        ensureIncomeTags()
        ensureAccounts()
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

    /** 启动时补齐收入标签体系（独立协程，不阻塞建库校验，失败仅记日志）。 */
    private fun ensureIncomeTags() {
        applicationScope.launch {
            runCatching { ensureIncomeTagsUseCase() }
                .onFailure { Log.e(INCOME_TAGS_TAG, "收入标签补齐失败", it) }
        }
    }

    /** 启动时补齐预置账户体系（独立协程，不阻塞建库校验，失败仅记日志）。 */
    private fun ensureAccounts() {
        applicationScope.launch {
            runCatching { ensureAccountsUseCase() }
                .onFailure { Log.e(ACCOUNTS_TAG, "预置账户补齐失败", it) }
        }
    }

    companion object {
        private const val TAG = "YunayuDB"
        private const val REPORT_TAG = "YunayuReport"
        private const val INCOME_TAGS_TAG = "YunayuIncomeTags"
        private const val ACCOUNTS_TAG = "YunayuAccounts"
    }
}
