package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.model.NlParseFailure
import com.expfal.yunayu.domain.nl.model.NlParseResult
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 把中文自由文本解析为归一化交易草稿的编排用例。
 *
 * 编排链路：空输入与引擎可用性前置校验 → 组装「根名 / 根名·子名」候选标签集 → 构建
 * systemInstruction 并调用引擎 → 解析原始输出为草稿 → 归一匹配标签短语回填 `tagId`。
 * 任何非取消异常降级为 [NlParseFailure.ENGINE_UNAVAILABLE]，[kotlinx.coroutines.CancellationException] 直接重抛。
 */
class ParseNaturalLanguageTransactionUseCase(
    private val parser: NLTransactionParser,
    private val tagRepository: TagRepository,
) {

    /**
     * 解析 [userText]，返回成功草稿或失败原因；[nowEpochMillis] 供相对日期折算与测试注入。
     */
    suspend operator fun invoke(
        userText: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): NlParseResult = try {
        parseOrThrow(userText, nowEpochMillis)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE)
    }

    private suspend fun parseOrThrow(userText: String, nowEpochMillis: Long): NlParseResult {
        val input = userText.trim()
        if (input.isEmpty()) return NlParseResult.Failure(NlParseFailure.EMPTY_INPUT)
        if (!parser.isAvailable()) return NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE)

        val candidates = loadTagCandidates()
        val instruction = NlPromptBuilder.build(candidates.displayNames)
        val raw = parser.generate(instruction, input)
            ?: return NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE)

        val draft = NlOutputParser.parseToDraft(raw, nowEpochMillis, input)
            ?: return NlParseResult.Failure(parseFailureFor(raw))

        return NlParseResult.Success(
            draft.copy(tagId = resolveTagId(draft.tagPhrase, candidates.idByDisplayName)),
        )
    }

    /** 加载根标签与各级子标签，组装展示名候选集及「归一展示名 → tagId」映射。 */
    private suspend fun loadTagCandidates(): TagCandidates {
        val roots = loadChildren(null)
        val displayNames = mutableListOf<String>()
        val idByDisplayName = mutableMapOf<String, Long>()
        for (root in roots) {
            displayNames += root.name
            idByDisplayName[normalize(root.name)] = root.id
            for (child in loadChildren(root.id)) {
                val display = "${root.name}·${child.name}"
                displayNames += display
                idByDisplayName[normalize(display)] = child.id
            }
        }
        return TagCandidates(displayNames, idByDisplayName)
    }

    /** 单层标签加载，失败降级为空列表，取消异常直接重抛。 */
    private suspend fun loadChildren(parentId: Long?): List<Tag> =
        runCatching { tagRepository.getChildren(parentId) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())

    /** 原始输出未产出草稿时，据是否含 JSON 片段区分「缺金额」与「输出畸形」。 */
    private fun parseFailureFor(raw: String): NlParseFailure {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            NlParseFailure.NO_AMOUNT
        } else {
            NlParseFailure.MALFORMED_OUTPUT
        }
    }

    /** 归一匹配标签短语到候选集；未命中返回 `null`。 */
    private fun resolveTagId(tagPhrase: String?, idByDisplayName: Map<String, Long>): Long? {
        val normalized = normalize(tagPhrase ?: return null)
        return idByDisplayName[normalized]
    }

    /** 去除全部空白字符做归一化，容忍「生活 · 餐饮」与「生活·餐饮」等写法差异。 */
    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }

    private data class TagCandidates(
        val displayNames: List<String>,
        val idByDisplayName: Map<String, Long>,
    )
}
