package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.OrganizeRecord
import com.expfal.yunayu.domain.nl.model.OrganizeSuggestion
import kotlinx.coroutines.CancellationException

/**
 * 批量整理建议编排用例。
 *
 * 降级语义：记录为空、引擎不可用、[NLTransactionParser.generate] 返回 `null`，或解析阶段
 * 发生任何非取消异常，均返回空列表；[kotlinx.coroutines.CancellationException] 直接重抛，
 * 遵守协程取消语义。
 */
class OrganizeSuggestUseCase(
    private val parser: NLTransactionParser,
) {

    /** 依据 [records]、候选标签 [candidates] 与收入根名 [incomeRootName] 产出整理建议列表。 */
    suspend operator fun invoke(
        records: List<OrganizeRecord>,
        candidates: List<String>,
        incomeRootName: String,
    ): List<OrganizeSuggestion> = try {
        suggestOrEmpty(records, candidates, incomeRootName)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        emptyList()
    }

    private suspend fun suggestOrEmpty(
        records: List<OrganizeRecord>,
        candidates: List<String>,
        incomeRootName: String,
    ): List<OrganizeSuggestion> {
        if (records.isEmpty()) return emptyList()
        if (!parser.isAvailable()) return emptyList()
        val instruction = OrganizePromptBuilder.build(records, candidates, incomeRootName)
        val raw = parser.generate(instruction, USER_TEXT) ?: return emptyList()
        return OrganizeOutputParser.parse(raw, records.map { it.id }.toSet())
    }

    private companion object {
        /** 记录已内嵌于 systemInstruction，userText 仅作触发指令。 */
        const val USER_TEXT = "请为以上未分类记录逐个给出标签建议"
    }
}
