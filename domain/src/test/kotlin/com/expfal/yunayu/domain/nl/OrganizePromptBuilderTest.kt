package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.model.OrganizeRecord
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [OrganizePromptBuilder] 的 JVM 单元测试。 */
class OrganizePromptBuilderTest {

    @Test
    fun `embeds candidates income root and output schema`() {
        val prompt = OrganizePromptBuilder.build(
            records = listOf(OrganizeRecord(7L, "兼职发传单", 5_000L, TransactionType.INCOME, 123L)),
            candidates = listOf("学习·教材", "入账·兼职"),
            incomeRootName = "入账",
        )

        assertTrue(prompt.contains("学习·教材"))
        assertTrue(prompt.contains("入账·兼职"))
        assertTrue(prompt.contains("入账"))
        assertTrue(prompt.contains("\"record_id\":7"))
        assertTrue(prompt.contains("兼职发传单"))
        assertTrue(prompt.contains("ATTACH"))
        assertTrue(prompt.contains("CREATE"))
        assertTrue(prompt.contains("root_name"))
        assertTrue(prompt.contains("INCOME"))
    }

    @Test
    fun `renders each record with id note amount and type`() {
        val prompt = OrganizePromptBuilder.build(
            records = listOf(
                OrganizeRecord(1L, "买教材", 2_500L, TransactionType.EXPENSE, 900L),
                OrganizeRecord(2L, null, 3_000L, TransactionType.INCOME, 1_000L),
            ),
            candidates = listOf("学习"),
            incomeRootName = "收入",
        )

        assertTrue(prompt.contains("\"record_id\":1"))
        assertTrue(prompt.contains("\"note\":\"买教材\""))
        assertTrue(prompt.contains("\"amount_cents\":2500"))
        assertTrue(prompt.contains("\"type\":\"EXPENSE\""))
        assertTrue(prompt.contains("\"record_id\":2"))
        assertTrue(prompt.contains("\"note\":\"\""))
        assertTrue(prompt.contains("\"type\":\"INCOME\""))
    }

    @Test
    fun `renders no candidates placeholder when list empty`() {
        val prompt = OrganizePromptBuilder.build(
            records = listOf(OrganizeRecord(1L, "买书", 1_000L, TransactionType.EXPENSE, 1L)),
            candidates = emptyList(),
            incomeRootName = "收入",
        )

        assertTrue(prompt.contains("无"))
    }

    @Test
    fun `escapes quotes backslashes and newlines in note`() {
        val note = "他说\"你好\"\\路径\n第二行"
        val prompt = OrganizePromptBuilder.build(
            records = listOf(OrganizeRecord(1L, note, 1_000L, TransactionType.EXPENSE, 1L)),
            candidates = emptyList(),
            incomeRootName = "收入",
        )

        // 引号 / 反斜杠 / 换行均应转义，避免破坏提示词 JSON 结构且保留原始内容。
        assertTrue(prompt.contains("\\\""))
        assertTrue(prompt.contains("\\\\"))
        assertTrue(prompt.contains("\\n"))
        assertTrue(prompt.contains("第二行"))
    }
}
