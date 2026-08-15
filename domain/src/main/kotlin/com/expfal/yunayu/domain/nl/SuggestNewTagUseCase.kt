package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.model.TagSuggestion
import kotlinx.coroutines.CancellationException

/**
 * 建议新标签的编排用例：基于输入短语与候选根类，调用引擎产出「新标签名 + 所属根类」建议。
 *
 * 编排链路：空输入与引擎可用性前置校验 → 组装根类名候选集 → 构建 systemInstruction 并调用引擎
 * → 解析原始输出为 [TagSuggestion]。任何非取消异常降级为 `null`（静默降级，domain 无日志），
 * [kotlinx.coroutines.CancellationException] 直接重抛。
 */
class SuggestNewTagUseCase(
    private val parser: NLTransactionParser,
) {

    /** 建议新标签；输入为空、引擎不可用、输出非法或推理异常时返回 `null`。 */
    suspend operator fun invoke(input: String, candidateRoots: List<Tag>): TagSuggestion? = try {
        suggestOrNull(input, candidateRoots)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        null
    }

    private suspend fun suggestOrNull(input: String, candidateRoots: List<Tag>): TagSuggestion? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (!parser.isAvailable()) return null
        val rootNames = candidateRoots.map { it.name }
        val instruction = TagSuggestionPromptBuilder.build(trimmed, rootNames)
        val raw = parser.generate(instruction, trimmed) ?: return null
        return TagSuggestionOutputParser.parse(raw, rootNames)
    }
}
