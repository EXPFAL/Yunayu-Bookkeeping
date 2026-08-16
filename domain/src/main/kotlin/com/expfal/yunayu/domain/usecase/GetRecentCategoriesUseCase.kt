package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 预测用户最近常用的「叶子」标签，支撑「3秒极速记账」首页快捷入口。
 *
 * 单一返回路径：先取过去 7 天内按交易频次降序的已用标签（最多 [DEFAULT_LIMIT] 个），
 * 过滤掉「拥有子标签」的父类仅保留叶子；再按 [type] 收支方向用叶子标签补足缺口
 * （与已用标签按 `id` 去重），最终裁剪到 [DEFAULT_LIMIT] 个。
 *
 * 叶子判定基于「是否拥有子标签」而非 [Tag.parentId]（推荐结果的 `parentId` 可能缺失）：
 * 经 [TagRepository.getChildren] 先取根再逐根取子，构建「拥有子级的父类 id」集合。
 * 支出用各支出根类的子标签平铺补足（排除收入根子树），收入仅用收入根的子标签补足；
 * 无子标签的根按叶子处理，保证入口始终可用。
 *
 * [TagRepository.getChildren] 查询失败时叶子判定不可得，降级为 recent 原样保留、补足为空，
 * 不崩溃（[CancellationException] 照常重抛）。
 */
class GetRecentCategoriesUseCase(
    private val tagRepository: TagRepository,
) {

    /** 返回按 [type] 收支方向统计、补足后的最近常用叶子标签（去重后不超过 [DEFAULT_LIMIT] 个）。 */
    suspend operator fun invoke(type: TransactionType, nowEpochMillis: Long = System.currentTimeMillis()): List<Tag> {
        val recent = tagRepository.getRecentUsedTags(
            sinceEpochMillis = nowEpochMillis - SEVEN_DAYS_MILLIS,
            type = type,
            limit = DEFAULT_LIMIT,
        )

        val roots = runCatching { tagRepository.getChildren(parentId = null) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return recent.take(DEFAULT_LIMIT)

        val childrenByRootId = mutableMapOf<Long, List<Tag>>()
        for (root in roots) {
            val children = runCatching { tagRepository.getChildren(parentId = root.id) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: return recent.take(DEFAULT_LIMIT)
            childrenByRootId[root.id] = children
        }

        val parentIdsWithChildren = childrenByRootId.filterValues { it.isNotEmpty() }.keys
        val recentLeaves = recent.filter { it.id !in parentIdsWithChildren }

        val fallback = when (type) {
            TransactionType.EXPENSE ->
                roots
                    .filter { it.name != IncomeTags.INCOME_ROOT_NAME }
                    .flatMap { root ->
                        val children = childrenByRootId[root.id].orEmpty()
                        if (children.isEmpty()) listOf(root) else children
                    }
            TransactionType.INCOME -> {
                val incomeRoot = roots.firstOrNull { it.name == IncomeTags.INCOME_ROOT_NAME }
                if (incomeRoot == null) {
                    emptyList()
                } else {
                    val children = childrenByRootId[incomeRoot.id].orEmpty()
                    if (children.isEmpty()) listOf(incomeRoot) else children
                }
            }
        }

        return (recentLeaves + fallback.filter { leaf -> recentLeaves.none { it.id == leaf.id } })
            .take(DEFAULT_LIMIT)
    }

    private companion object {
        /** 最近常用标签回溯窗口：7 天（毫秒）。 */
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** 快捷入口最多展示的标签数。 */
        const val DEFAULT_LIMIT = 4
    }
}
