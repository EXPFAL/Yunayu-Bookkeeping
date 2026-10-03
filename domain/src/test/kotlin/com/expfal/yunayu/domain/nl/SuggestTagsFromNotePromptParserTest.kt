package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [SuggestTagsFromNotePromptBuilder] / [SuggestTagsFromNoteOutputParser] 纯函数单测。 */
class SuggestTagsFromNotePromptParserTest {

    @Test
    fun `prompt includes note and candidates`() {
        val text = SuggestTagsFromNotePromptBuilder.build(
            note = "买教材",
            type = TransactionType.EXPENSE,
            candidates = listOf("学习·课本教辅", "生活"),
        )
        assertTrue(text.contains("买教材"))
        assertTrue(text.contains("学习·课本教辅"))
        assertTrue(text.contains("EXPENSE"))
    }

    @Test
    fun `parser keeps valid names only up to max`() {
        val raw = """
            [{"tag_name":"学习·课本教辅"},{"tag_name":"幽灵"},{"tag_name":"生活"},{"tag_name":"娱乐"}]
        """.trimIndent()
        val result = SuggestTagsFromNoteOutputParser.parse(
            raw = raw,
            validNames = setOf("学习·课本教辅", "生活", "娱乐"),
            maxCount = 2,
        )
        assertEquals(listOf("学习·课本教辅", "生活"), result)
    }

    @Test
    fun `parser returns empty for non array`() {
        assertTrue(
            SuggestTagsFromNoteOutputParser.parse(
                raw = "没有数组",
                validNames = setOf("学习"),
            ).isEmpty(),
        )
    }
}
