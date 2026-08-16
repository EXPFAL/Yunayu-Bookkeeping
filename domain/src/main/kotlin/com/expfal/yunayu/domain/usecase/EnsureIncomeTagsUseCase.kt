package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.repository.TagRepository

/**
 * 应用启动时的收入标签体系补齐编排用例。
 *
 * 保证「收入」根类及其种子子标签存在（首启新建或存量库补齐），根类仅经
 * [TagRepository.addRootTag] 白名单创建。子标签按名幂等补齐：已存在则跳过，新建计入
 * [SeedResult.createdChildren]，竞态命中 [DuplicateTagNameException] 计入 [SeedResult.skippedChildren]；
 * 其余异常（含 [kotlinx.coroutines.CancellationException]）上抛由调用方兜底。
 */
class EnsureIncomeTagsUseCase(
    private val tagRepository: TagRepository,
) {

    /** 一次补齐的结果快照。 */
    data class SeedResult(
        val rootCreated: Boolean,
        val createdChildren: List<String>,
        val skippedChildren: List<String>,
    )

    /** 确保收入根类与种子子标签存在，返回补齐结果。 */
    suspend operator fun invoke(): SeedResult {
        var rootCreated = false
        val incomeRootId = tagRepository.getChildren(parentId = null)
            .firstOrNull { it.name == IncomeTags.INCOME_ROOT_NAME }
            ?.id
            ?: try {
                rootCreated = true
                tagRepository.addRootTag(IncomeTags.INCOME_ROOT_NAME, IncomeTags.INCOME_ROOT_ICON)
            } catch (e: DuplicateTagNameException) {
                // 并发双插竞态：根已由其它协程/进程创建，标记 skipped 并回读其 id，避免启动链路抛异常。
                rootCreated = false
                tagRepository.getChildren(parentId = null)
                    .firstOrNull { it.name == IncomeTags.INCOME_ROOT_NAME }
                    ?.id
                    ?: throw e
            }
        val existingNames = tagRepository.getChildren(parentId = incomeRootId).map { it.name }.toMutableSet()
        val createdChildren = mutableListOf<String>()
        val skippedChildren = mutableListOf<String>()
        for (name in IncomeTags.INCOME_SEED_SUB_TAGS) {
            if (name in existingNames) continue
            try {
                tagRepository.addSubTag(parentId = incomeRootId, name = name, icon = null)
                createdChildren += name
                existingNames += name
            } catch (e: DuplicateTagNameException) {
                skippedChildren += name
            }
        }
        return SeedResult(rootCreated, createdChildren, skippedChildren)
    }
}
