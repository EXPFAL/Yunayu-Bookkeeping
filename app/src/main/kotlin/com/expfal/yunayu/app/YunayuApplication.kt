package com.expfal.yunayu.app

import android.app.Application
import android.util.Log
import com.expfal.yunayu.domain.repository.TagRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hilt 入口。启动时触发一次 Room 建表 + 查询，验证数据库真实初始化。 */
@HiltAndroidApp
class YunayuApplication : Application() {

    @Inject
    lateinit var tagRepository: TagRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        verifyDatabase()
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

    companion object {
        private const val TAG = "YunayuDB"
    }
}
