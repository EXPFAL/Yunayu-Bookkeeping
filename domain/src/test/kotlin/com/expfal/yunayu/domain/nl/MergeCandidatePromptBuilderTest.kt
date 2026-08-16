package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.TagPairInfo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [MergeCandidatePromptBuilder] 的 JVM 单元测试。 */
class MergeCandidatePromptBuilderTest {

    @Test
    fun `embeds each pair name and count`() {
        val prompt = MergeCandidatePromptBuilder.build(
            listOf(
                TagPairInfo(nameA = "餐饮", nameB = "吃饭", countA = 12, countB = 3),
                TagPairInfo(nameA = "兼职", nameB = "打工", countA = 5, countB = 2),
            ),
        )

        assertTrue(prompt.contains("\"tag_a\":\"餐饮\""))
        assertTrue(prompt.contains("\"tag_b\":\"吃饭\""))
        assertTrue(prompt.contains("\"count_a\":12"))
        assertTrue(prompt.contains("\"count_b\":3"))
        assertTrue(prompt.contains("\"tag_a\":\"兼职\""))
        assertTrue(prompt.contains("\"tag_b\":\"打工\""))
        assertTrue(prompt.contains("\"count_a\":5"))
        assertTrue(prompt.contains("\"count_b\":2"))
    }

    @Test
    fun `declares decision codes and output schema`() {
        val prompt = MergeCandidatePromptBuilder.build(listOf(TagPairInfo("餐饮", "吃饭", 12, 3)))

        assertTrue(prompt.contains("A_INTO_B"))
        assertTrue(prompt.contains("B_INTO_A"))
        assertTrue(prompt.contains("KEEP_BOTH"))
        assertTrue(prompt.contains("tag_a"))
        assertTrue(prompt.contains("tag_b"))
        assertTrue(prompt.contains("decision"))
    }

    @Test
    fun `contains a single few-shot example`() {
        val prompt = MergeCandidatePromptBuilder.build(listOf(TagPairInfo("餐饮", "吃饭", 12, 3)))

        assertTrue(prompt.contains("示例"))
        assertTrue(prompt.contains("\"decision\":\"A_INTO_B\""))
    }
}
