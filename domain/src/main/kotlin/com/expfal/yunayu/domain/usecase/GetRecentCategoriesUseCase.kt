package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.TagRepository

/**
 * 预测用户最近常用的标签类别，支撑「3秒极速记账」首页快捷入口。
 *
 * 优先返回过去 7 天内按交易频次降序的已用标签（最多 [DEFAULT_LIMIT] 个）；
 * 若该时间窗内没有任何交易记录，则回退为全部根节点标签，保证入口始终可用。
 */
class GetRecentCategoriesUseCase(
    private val tagRepository: TagRepository,
) {

    /** 返回最近常用标签；近 7 天无记录时回退根标签。 */
    suspend operator fun invoke(nowEpochMillis: Long = System.currentTimeMillis()): List<Tag> {
        val recent = tagRepository.getRecentUsedTags(
            sinceEpochMillis = nowEpochMillis - SEVEN_DAYS_MILLIS,
            limit = DEFAULT_LIMIT,
        )
        return if (recent.isEmpty()) {
            tagRepository.getChildren(parentId = null)
        } else {
            recent
        }
    }

    private companion object {
        /** 最近常用标签回溯窗口：7 天（毫秒）。 */
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** 快捷入口最多展示的标签数。 */
        const val DEFAULT_LIMIT = 4
    }
}
