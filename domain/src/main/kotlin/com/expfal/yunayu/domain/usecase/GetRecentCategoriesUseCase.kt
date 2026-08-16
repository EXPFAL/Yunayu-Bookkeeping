package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 预测用户最近常用的标签类别，支撑「3秒极速记账」首页快捷入口。
 *
 * 单一返回路径：先取过去 7 天内按交易频次降序的已用标签（最多 [DEFAULT_LIMIT] 个），
 * 再按 [type] 收支方向补足缺口（与已用标签按 `id` 去重），最终裁剪到 [DEFAULT_LIMIT] 个。
 * 支出仅用四大根类补足（排除收入根），收入仅用收入根及其子标签补足；根节点不足时按实际
 * 数量返回，保证入口始终可用。
 */
class GetRecentCategoriesUseCase(
    private val tagRepository: TagRepository,
) {

    /** 返回按 [type] 收支方向统计、补足后的最近常用标签（去重后不超过 [DEFAULT_LIMIT] 个）。 */
    suspend operator fun invoke(type: TransactionType, nowEpochMillis: Long = System.currentTimeMillis()): List<Tag> {
        val recent = tagRepository.getRecentUsedTags(
            sinceEpochMillis = nowEpochMillis - SEVEN_DAYS_MILLIS,
            type = type,
            limit = DEFAULT_LIMIT,
        )
        val roots = runCatching { tagRepository.getChildren(parentId = null) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())
        val fallback = when (type) {
            TransactionType.EXPENSE -> roots.filter { it.name != IncomeTags.INCOME_ROOT_NAME }
            TransactionType.INCOME -> {
                val incomeRoot = roots.firstOrNull { it.name == IncomeTags.INCOME_ROOT_NAME }
                if (incomeRoot == null) {
                    emptyList()
                } else {
                    listOf(incomeRoot) + runCatching { tagRepository.getChildren(parentId = incomeRoot.id) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(emptyList())
                }
            }
        }
        return (recent + fallback.filter { root -> recent.none { it.id == root.id } })
            .take(DEFAULT_LIMIT)
    }

    private companion object {
        /** 最近常用标签回溯窗口：7 天（毫秒）。 */
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** 快捷入口最多展示的标签数。 */
        const val DEFAULT_LIMIT = 4
    }
}
