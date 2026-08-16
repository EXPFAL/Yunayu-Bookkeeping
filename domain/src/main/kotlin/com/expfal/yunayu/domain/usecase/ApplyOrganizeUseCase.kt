package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 把已确认的整理建议落到交易标签并标脏受影响报告。
 *
 * 流程：加载现有标签全集 → 逐条解析（ATTACH 匹配已有标签 / CREATE 新建或复用同名标签，
 * 失败计入 [ApplyResult.failedRecordIds]）→ 成功项按 tagId 分组后单事务 [TransactionRepository.assignTags]
 * → 对受影响记录去重后的 `occurredAt` 逐个标脏窗口覆盖报告（标脏失败不阻断，仅
 * [kotlinx.coroutines.CancellationException] 重抛）。
 */
class ApplyOrganizeUseCase(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val reportRepository: ReportRepository,
) {

    /** 一条已确认的整理项：动作 + 目标标签名 + 可选根类名。 */
    data class ConfirmedItem(
        val recordId: Long,
        val action: Action,
        val tagName: String,
        val rootName: String?,
    )

    /** 应用结果：成功应用条数、失败交易 id 列表（保持输入顺序）与因同名复用挂载的标签名（去重）。 */
    data class ApplyResult(
        val appliedCount: Int,
        val failedRecordIds: List<Long>,
        val reusedTagNames: List<String>,
    )

    /** 应用 [items]（结合 [recordsById] 查交易类型与发生时间），返回应用结果。 */
    suspend operator fun invoke(
        items: List<ConfirmedItem>,
        recordsById: Map<Long, RecentTransaction>,
    ): ApplyResult {
        val roots = loadChildren(parentId = null)
        val childrenByRoot = roots.associate { root -> root.id to loadChildren(root.id) }

        val failed = mutableListOf<Long>()
        val reusedTagNames = linkedSetOf<String>()
        val assignments = linkedMapOf<Long, MutableList<Long>>()
        for (item in items) {
            val tagId = resolveTagId(item, recordsById[item.recordId], roots, childrenByRoot, reusedTagNames)
            if (tagId == null) {
                failed += item.recordId
                continue
            }
            assignments.getOrPut(tagId) { mutableListOf() } += item.recordId
        }

        if (assignments.isNotEmpty()) {
            transactionRepository.assignTags(assignments.mapValues { it.value.toList() })
        }

        val affectedOccurredAt = items
            .filter { it.recordId !in failed }
            .mapNotNull { recordsById[it.recordId]?.occurredAt }
            .distinct()
        for (occurredAt in affectedOccurredAt) {
            runCatching { reportRepository.invalidateWhereWindowContains(occurredAt) }
                .onFailure { if (it is CancellationException) throw it }
        }

        return ApplyResult(
            appliedCount = items.count { it.recordId !in failed },
            failedRecordIds = failed,
            reusedTagNames = reusedTagNames.toList(),
        )
    }

    /** 按动作解析目标 tagId；无法解析（含交易缺失）返回 `null`，复用命中收集至 [reusedTagNames]。 */
    private suspend fun resolveTagId(
        item: ConfirmedItem,
        record: RecentTransaction?,
        roots: List<Tag>,
        childrenByRoot: Map<Long, List<Tag>>,
        reusedTagNames: MutableSet<String>,
    ): Long? {
        if (record == null) return null
        return when (item.action) {
            Action.ATTACH -> resolveAttach(item.tagName, record, roots, childrenByRoot)
            Action.CREATE -> resolveCreate(item, record, roots, childrenByRoot, reusedTagNames)
        }
    }

    /**
     * ATTACH：优先「根·子」全名匹配，否则裸名唯一匹配；不唯一、未命中或目标体系与记录
     * 收支方向不符（INCOME 必须收入体系、EXPENSE 必须非收入体系）返回 `null`。
     */
    private fun resolveAttach(
        tagName: String,
        record: RecentTransaction,
        roots: List<Tag>,
        childrenByRoot: Map<Long, List<Tag>>,
    ): Long? {
        val target = tagName.trim()
        if (target.isEmpty()) return null
        val normalized = normalize(target)
        for (root in roots) {
            val child = childrenByRoot[root.id].orEmpty()
                .firstOrNull { normalize("${root.name}·${it.name}") == normalized }
            if (child != null) {
                return if (matchesIncomeSystem(root.name, record.type)) child.id else null
            }
        }
        val bareMatches = (roots + childrenByRoot.values.flatten())
            .filter { normalize(it.name) == normalized }
        val matched = bareMatches.singleOrNull() ?: return null
        val rootName = rootNameOf(matched, roots) ?: return null
        return if (matchesIncomeSystem(rootName, record.type)) matched.id else null
    }

    /**
     * CREATE：根白名单校验 + 收支双向校验（INCOME 必须收入根、EXPENSE 必须非收入根）
     * → 新建或复用同名子标签（复用计入 [reusedTagNames]）。
     */
    private suspend fun resolveCreate(
        item: ConfirmedItem,
        record: RecentTransaction,
        roots: List<Tag>,
        childrenByRoot: Map<Long, List<Tag>>,
        reusedTagNames: MutableSet<String>,
    ): Long? {
        val rootName = item.rootName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val root = roots.firstOrNull { it.name == rootName } ?: return null
        if (!matchesIncomeSystem(rootName, record.type)) return null
        val name = item.tagName.trim().takeIf { it.isNotEmpty() } ?: return null
        return try {
            tagRepository.addSubTag(parentId = root.id, name = name, icon = null)
        } catch (e: DuplicateTagNameException) {
            reusedTagNames += name
            childrenByRoot[root.id]
                ?.firstOrNull { it.name == name }
                ?.id
                ?: tagRepository.getChildren(root.id).firstOrNull { it.name == name }?.id
        }
    }

    /** 单层标签加载，失败降级为空列表，取消异常直接重抛。 */
    private suspend fun loadChildren(parentId: Long?): List<Tag> =
        runCatching { tagRepository.getChildren(parentId) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())

    /** 记录收支方向是否匹配目标标签所属体系：INCOME ↔ 收入根，EXPENSE ↔ 非收入根。 */
    private fun matchesIncomeSystem(rootName: String, type: TransactionType): Boolean =
        (rootName == IncomeTags.INCOME_ROOT_NAME) == (type == TransactionType.INCOME)

    /** 返回标签所属根名：根节点即自身，子节点反查其根；找不到返回 `null`。 */
    private fun rootNameOf(tag: Tag, roots: List<Tag>): String? =
        if (tag.parentId == null) tag.name else roots.firstOrNull { it.id == tag.parentId }?.name

    /** 去除全部空白字符做归一化，容忍「生活 · 餐饮」与「生活·餐饮」等写法差异。 */
    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }
}
