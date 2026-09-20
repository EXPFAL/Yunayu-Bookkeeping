package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 根据备注即时建议已有标签（最多 3 个 ATTACH）。
 *
 * 无 API / 失败 / 无把握 → 空列表，不抛给 UI。
 */
class SuggestTagsFromNoteUseCase(
    private val parser: NLTransactionParser,
    private val tagRepository: TagRepository,
) {

    suspend operator fun invoke(
        note: String,
        type: TransactionType,
    ): List<Tag> {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!parser.isAvailable()) return emptyList()
        return try {
            val candidates = loadTagCandidates(type)
            if (candidates.displayNames.isEmpty()) return emptyList()
            val instruction = SuggestTagsFromNotePromptBuilder.build(
                note = trimmed,
                type = type,
                candidates = candidates.displayNames,
            )
            val raw = parser.generate(instruction, USER_TEXT) ?: return emptyList()
            val names = SuggestTagsFromNoteOutputParser.parse(
                raw = raw,
                validNames = candidates.displayNames.toSet(),
                maxCount = MAX_SUGGESTIONS,
            )
            names.mapNotNull { name ->
                val id = candidates.idByDisplayName[normalize(name)] ?: return@mapNotNull null
                candidates.tagsById[id]
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun loadTagCandidates(type: TransactionType): TagCandidates {
        val roots = loadChildren(null).filter { root ->
            val isIncome = root.name == IncomeTags.INCOME_ROOT_NAME
            if (type == TransactionType.INCOME) isIncome else !isIncome
        }
        val displayNames = mutableListOf<String>()
        val idByDisplayName = mutableMapOf<String, Long>()
        val tagsById = mutableMapOf<Long, Tag>()
        for (root in roots) {
            displayNames += root.name
            idByDisplayName[normalize(root.name)] = root.id
            tagsById[root.id] = root
            for (child in loadChildren(root.id)) {
                val display = "${root.name}·${child.name}"
                displayNames += display
                idByDisplayName[normalize(display)] = child.id
                tagsById[child.id] = child
            }
        }
        return TagCandidates(displayNames, idByDisplayName, tagsById)
    }

    private suspend fun loadChildren(parentId: Long?): List<Tag> =
        runCatching { tagRepository.getChildren(parentId) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())

    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }

    private data class TagCandidates(
        val displayNames: List<String>,
        val idByDisplayName: Map<String, Long>,
        val tagsById: Map<Long, Tag>,
    )

    private companion object {
        const val USER_TEXT = "请根据备注给出标签建议"
        const val MAX_SUGGESTIONS = 3
    }
}
