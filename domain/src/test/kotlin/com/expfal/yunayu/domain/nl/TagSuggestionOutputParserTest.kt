package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.TagSuggestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [TagSuggestionOutputParser] 的 JVM 单元测试。 */
class TagSuggestionOutputParserTest {

    private val candidates = listOf("生活", "学习")

    @Test
    fun `parses normal json`() {
        val suggestion = TagSuggestionOutputParser.parse(
            "{\"tag_name\":\"咖啡\",\"root\":\"生活\"}",
            candidates,
        )

        assertEquals(TagSuggestion("咖啡", "生活"), suggestion)
    }

    @Test
    fun `parses tag_name containing escaped quote`() {
        val suggestion = TagSuggestionOutputParser.parse(
            "{\"tag_name\":\"咖\\\"啡\",\"root\":\"生活\"}",
            candidates,
        )

        assertEquals(TagSuggestion("咖\"啡", "生活"), suggestion)
    }

    @Test
    fun `extracts json wrapped in prose`() {
        val suggestion = TagSuggestionOutputParser.parse(
            "建议如下：{\"tag_name\":\"球鞋\",\"root\":\"娱乐\"}，请确认",
            candidates + "娱乐",
        )

        assertEquals(TagSuggestion("球鞋", "娱乐"), suggestion)
    }

    @Test
    fun `returns null when root not in candidates`() {
        assertNull(
            TagSuggestionOutputParser.parse("{\"tag_name\":\"咖啡\",\"root\":\"娱乐\"}", candidates),
        )
    }

    @Test
    fun `returns null when tag_name missing`() {
        assertNull(
            TagSuggestionOutputParser.parse("{\"root\":\"生活\"}", candidates),
        )
    }

    @Test
    fun `returns null when tag_name blank`() {
        assertNull(
            TagSuggestionOutputParser.parse("{\"tag_name\":\"  \",\"root\":\"生活\"}", candidates),
        )
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(TagSuggestionOutputParser.parse("没有任何 JSON", candidates))
    }
}
