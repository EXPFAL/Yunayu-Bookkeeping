package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MergeCandidate
import com.expfal.yunayu.domain.model.MergeDecision
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.MergeCandidateOutputParser
import com.expfal.yunayu.domain.nl.MergeCandidatePromptBuilder
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.model.TagPairInfo
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 找出可合并的语义重复标签对候选。
 *
 * 编排链路：加载全量标签 → 排除根类与带子级者（仅保留叶子）→ 经 [TagRepository.getDeleteImpact]
 * 取每叶子的直接交易记录数（叶子无子树，`affectedTransactionCount` 即其记录数）→ 预筛
 * `countA + countB > 3` 的标签对 → 同根优先排序取上限 [MAX_PAIRS] 对 → 引擎不可用返回空 →
 * 每 [BATCH_SIZE] 对为一批调用引擎 → 解析并过滤 [MergeDecision.KEEP_BOTH] 后返回合并类候选。
 *
 * 降级语义：引擎不可用、[NLTransactionParser.generate] 返回 `null`、标签加载失败或解析阶段
 * 发生任何非取消异常，均返回空列表；[kotlinx.coroutines.CancellationException] 直接重抛。
 */
class FindMergeCandidatesUseCase(
    private val tagRepository: TagRepository,
    private val parser: NLTransactionParser,
) {

    /** 返回语义重复的叶子标签对合并候选（不含 KEEP_BOTH）。 */
    suspend operator fun invoke(): List<MergeCandidate> = try {
        findOrEmpty()
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        emptyList()
    }

    private suspend fun findOrEmpty(): List<MergeCandidate> {
        if (!parser.isAvailable()) return emptyList()
        val leaves = loadLeaves()
        val pairs = buildPairs(leaves)
        if (pairs.isEmpty()) return emptyList()

        val countByTagId = leaves.associate { it.tag.id to it.count }
        // 跨根同名叶子存在歧义：同名出现多次则该名字不纳入映射，避免解析结果挂错标签。
        val tagByName = leaves
            .groupBy { it.tag.name }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single().tag }

        val results = mutableListOf<MergeCandidate>()
        for (batch in pairs.chunked(BATCH_SIZE)) {
            val pairInfos = batch.map { (a, b) -> TagPairInfo(a.tag.name, b.tag.name, a.count, b.count) }
            val validPairs = pairInfos.flatMap { listOf(it.nameA to it.nameB, it.nameB to it.nameA) }.toSet()
            val raw = parser.generate(MergeCandidatePromptBuilder.build(pairInfos), USER_TEXT)
                ?: continue
            for (parsed in MergeCandidateOutputParser.parse(raw, validPairs)) {
                if (parsed.decision == MergeDecision.KEEP_BOTH) continue
                val tagA = tagByName[parsed.tagA] ?: continue
                val tagB = tagByName[parsed.tagB] ?: continue
                results += MergeCandidate(
                    tagA = tagA,
                    tagB = tagB,
                    countA = countByTagId.getValue(tagA.id),
                    countB = countByTagId.getValue(tagB.id),
                    decision = parsed.decision,
                )
            }
        }
        // LLM 可能对同一标签对重复输出，按 (tagA.id, tagB.id) 去重，避免重复候选。
        return results.distinctBy { it.tagA.id to it.tagB.id }
    }

    /** 广度遍历加载全量标签（根 → 逐层子节点），失败向上抛交由外层降级。 */
    private suspend fun loadAllTags(): List<Tag> {
        val result = mutableListOf<Tag>()
        val queue = ArrayDeque<Tag>()
        queue.addAll(tagRepository.getChildren(parentId = null))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result += current
            queue.addAll(tagRepository.getChildren(current.id))
        }
        return result
    }

    /** 加载叶子标签及其直接交易记录数（排除根类与带子级者）。 */
    private suspend fun loadLeaves(): List<LeafInfo> {
        val all = loadAllTags()
        val leaves = all.filter { tag ->
            tag.parentId != null && all.none { it.parentId == tag.id }
        }
        return leaves.map { tag ->
            LeafInfo(tag = tag, count = tagRepository.getDeleteImpact(tag.id).affectedTransactionCount)
        }
    }

    /** 组合叶子标签对：预筛合计记录数 > 3，同根优先，按合计降序取上限 [MAX_PAIRS] 对。 */
    private fun buildPairs(leaves: List<LeafInfo>): List<Pair<LeafInfo, LeafInfo>> {
        val pairs = mutableListOf<Pair<LeafInfo, LeafInfo>>()
        for (i in leaves.indices) {
            for (j in i + 1 until leaves.size) {
                val a = leaves[i]
                val b = leaves[j]
                if (a.count + b.count <= 3) continue
                pairs += a to b
            }
        }
        return pairs.sortedWith(
            compareByDescending<Pair<LeafInfo, LeafInfo>> {
                it.first.tag.parentId == it.second.tag.parentId
            }
                .thenByDescending { it.first.count + it.second.count }
                .thenBy { it.first.tag.id }
                .thenBy { it.second.tag.id },
        ).take(MAX_PAIRS)
    }

    /** 叶子标签及其直接交易记录数。 */
    private data class LeafInfo(
        val tag: Tag,
        val count: Int,
    )

    private companion object {
        /** 单次请求最多发送的标签对数。 */
        const val BATCH_SIZE = 15

        /** 候选对总数上限。 */
        const val MAX_PAIRS = 30

        /** 标签对已内嵌于 systemInstruction，userText 仅作触发指令。 */
        const val USER_TEXT = "请为以上标签对逐个给出合并决策"
    }
}
