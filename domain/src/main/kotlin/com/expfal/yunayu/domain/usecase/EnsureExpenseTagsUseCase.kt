package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.ExpenseSeedTags
import com.expfal.yunayu.domain.repository.TagRepository

/**
 * 存量库补齐生活类增量叶子：囤货三餐 / 外出就餐 / 经营进货。
 *
 * 不创建支出根类（四大根仅首次建库种子化）。无「生活」根时跳过，返回 [SeedResult.rootFound] = false。
 * 子标签按名幂等：已存在跳过，新建计入 [SeedResult.createdChildren]，
 * 竞态 [DuplicateTagNameException] 计入 [SeedResult.skippedChildren]。
 */
class EnsureExpenseTagsUseCase(
    private val tagRepository: TagRepository,
) {

    data class SeedResult(
        val rootFound: Boolean,
        val createdChildren: List<String>,
        val skippedChildren: List<String>,
    )

    suspend operator fun invoke(): SeedResult {
        val lifeRoot = tagRepository.getChildren(parentId = null)
            .firstOrNull { it.name == ExpenseSeedTags.LIFE_ROOT_NAME }
            ?: return SeedResult(rootFound = false, createdChildren = emptyList(), skippedChildren = emptyList())
        val existingNames = tagRepository.getChildren(parentId = lifeRoot.id).map { it.name }.toMutableSet()
        val createdChildren = mutableListOf<String>()
        val skippedChildren = mutableListOf<String>()
        for (name in ExpenseSeedTags.ENSURE_LIFE_CHILDREN) {
            if (name in existingNames) continue
            try {
                tagRepository.addSubTag(parentId = lifeRoot.id, name = name, icon = null)
                createdChildren += name
                existingNames += name
            } catch (_: DuplicateTagNameException) {
                skippedChildren += name
            }
        }
        return SeedResult(rootFound = true, createdChildren, skippedChildren)
    }
}
