package com.expfal.yunayu.domain.nl

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TagSuggestionPromptBuilder] 的 JVM 单元测试。 */
class TagSuggestionPromptBuilderTest {

    @Test
    fun `contains json schema keywords`() {
        val instruction = TagSuggestionPromptBuilder.build("每天买咖啡", listOf("生活", "学习"))

        assertTrue(instruction.contains("tag_name"))
        assertTrue(instruction.contains("root"))
    }

    @Test
    fun `contains candidate roots and input`() {
        val instruction = TagSuggestionPromptBuilder.build("每天买咖啡", listOf("生活", "学习"))

        assertTrue(instruction.contains("生活"))
        assertTrue(instruction.contains("学习"))
        assertTrue(instruction.contains("每天买咖啡"))
    }

    @Test
    fun `handles empty candidate set`() {
        val instruction = TagSuggestionPromptBuilder.build("每天买咖啡", emptyList())

        assertTrue(instruction.contains("无"))
    }
}
