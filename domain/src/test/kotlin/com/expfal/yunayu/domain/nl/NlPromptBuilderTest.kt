package com.expfal.yunayu.domain.nl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [NlPromptBuilder] 的 JVM 单元测试。 */
class NlPromptBuilderTest {

    @Test
    fun `contains schema keywords`() {
        val instruction = NlPromptBuilder.build(listOf("学习", "生活·外出就餐"))

        assertTrue(instruction.contains("amount"))
        assertTrue(instruction.contains("expense"))
        assertTrue(instruction.contains("income"))
        assertTrue(instruction.contains("tag"))
        assertTrue(instruction.contains("date"))
        assertTrue(instruction.contains("note"))
        assertTrue(instruction.contains("必填"))
        assertTrue(instruction.contains("2-8"))
    }

    @Test
    fun `contains passed tag names`() {
        val instruction = NlPromptBuilder.build(listOf("学习", "生活·外出就餐"))

        assertTrue(instruction.contains("学习"))
        assertTrue(instruction.contains("生活·外出就餐"))
    }

    @Test
    fun `few shot examples use stockpile and dining-out tags`() {
        val instruction = NlPromptBuilder.build(emptyList())

        assertTrue(instruction.contains("生活·外出就餐"))
        assertTrue(instruction.contains("生活·囤货三餐"))
    }

    @Test
    fun `handles empty candidate set`() {
        val instruction = NlPromptBuilder.build(emptyList())

        assertTrue(instruction.contains("无"))
    }

    @Test
    fun `every few-shot example demonstrates note field`() {
        val instruction = NlPromptBuilder.build(listOf("学习", "生活·外出就餐"))
        val exampleCount = Regex("→\\{").findAll(instruction).count()
        val noteCount = Regex("\"note\":").findAll(instruction).count()

        assertEquals(exampleCount, noteCount)
        assertTrue(exampleCount > 0)
    }
}
