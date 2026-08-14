package com.expfal.yunayu.domain.nl

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [NlPromptBuilder] 的 JVM 单元测试。 */
class NlPromptBuilderTest {

    @Test
    fun `contains schema keywords`() {
        val instruction = NlPromptBuilder.build(listOf("学习", "生活·餐饮"))

        assertTrue(instruction.contains("amount"))
        assertTrue(instruction.contains("expense"))
        assertTrue(instruction.contains("income"))
        assertTrue(instruction.contains("tag"))
        assertTrue(instruction.contains("date"))
    }

    @Test
    fun `contains passed tag names`() {
        val instruction = NlPromptBuilder.build(listOf("学习", "生活·餐饮"))

        assertTrue(instruction.contains("学习"))
        assertTrue(instruction.contains("生活·餐饮"))
    }

    @Test
    fun `handles empty candidate set`() {
        val instruction = NlPromptBuilder.build(emptyList())

        assertTrue(instruction.contains("无"))
    }
}
