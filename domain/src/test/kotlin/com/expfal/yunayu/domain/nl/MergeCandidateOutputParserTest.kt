package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.MergeDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [MergeCandidateOutputParser] 的 JVM 单元测试。 */
class MergeCandidateOutputParserTest {

    private val validPairs = setOf("餐饮" to "吃饭", "吃饭" to "餐饮")

    @Test
    fun `parses valid decision in short code form`() {
        val raw = """[{"tag_a":"餐饮","tag_b":"吃饭","decision":"A_INTO_B"}]"""

        val result = MergeCandidateOutputParser.parse(raw, validPairs)

        assertEquals(1, result.size)
        assertEquals("餐饮", result.single().tagA)
        assertEquals("吃饭", result.single().tagB)
        assertEquals(MergeDecision.MERGE_A_INTO_B, result.single().decision)
    }

    @Test
    fun `accepts reversed pair order`() {
        val raw = """[{"tag_a":"吃饭","tag_b":"餐饮","decision":"B_INTO_A"}]"""

        val result = MergeCandidateOutputParser.parse(raw, validPairs)

        assertEquals(1, result.size)
        assertEquals(MergeDecision.MERGE_B_INTO_A, result.single().decision)
    }

    @Test
    fun `skips pair not in validPairs and keeps valid`() {
        val raw =
            """[{"tag_a":"餐饮","tag_b":"吃饭","decision":"A_INTO_B"},{"tag_a":"外卖","tag_b":"聚餐","decision":"KEEP_BOTH"}]"""

        val result = MergeCandidateOutputParser.parse(raw, validPairs)

        assertEquals(1, result.size)
        assertEquals("餐饮", result.single().tagA)
    }

    @Test
    fun `skips item with illegal decision`() {
        val raw = """[{"tag_a":"餐饮","tag_b":"吃饭","decision":"REMOVE"}]"""

        assertTrue(MergeCandidateOutputParser.parse(raw, validPairs).isEmpty())
    }

    @Test
    fun `skips item with missing fields and keeps valid`() {
        val raw = """[{"tag_a":"餐饮"},{"tag_a":"餐饮","tag_b":"吃饭","decision":"KEEP_BOTH"}]"""

        val result = MergeCandidateOutputParser.parse(raw, validPairs)

        assertEquals(1, result.size)
        assertEquals(MergeDecision.KEEP_BOTH, result.single().decision)
    }

    @Test
    fun `returns empty for blank or non array output`() {
        assertTrue(MergeCandidateOutputParser.parse("", validPairs).isEmpty())
        assertTrue(MergeCandidateOutputParser.parse("没有任何数组", validPairs).isEmpty())
    }

    @Test
    fun `extracts array from surrounding prose`() {
        val raw = "判定结果如下：\n[{\"tag_a\":\"餐饮\",\"tag_b\":\"吃饭\",\"decision\":\"KEEP_BOTH\"}]\n完毕"

        val result = MergeCandidateOutputParser.parse(raw, validPairs)

        assertEquals(1, result.size)
        assertEquals(MergeDecision.KEEP_BOTH, result.single().decision)
    }
}
