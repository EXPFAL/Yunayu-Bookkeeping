package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.CategoryNoteSample
import com.expfal.yunayu.domain.report.model.CategoryShare
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ReportPromptBuilder] 纯函数单测（含备注抽样段）。 */
class ReportPromptBuilderTest {

    @Test
    fun `data text omits note section when samples empty`() {
        val text = ReportPromptBuilder.buildDataText(
            incomeCents = 100_00L,
            expenseCents = 50_00L,
            topCategories = listOf(CategoryShare("餐饮", 50_00L, 100, 1L)),
            prevIncomeCents = 80_00L,
            prevExpenseCents = 40_00L,
        )
        assertFalse(text.contains("各类代表备注"))
    }

    @Test
    fun `data text includes category notes`() {
        val text = ReportPromptBuilder.buildDataText(
            incomeCents = 100_00L,
            expenseCents = 50_00L,
            topCategories = listOf(CategoryShare("餐饮", 50_00L, 100, 1L)),
            prevIncomeCents = 80_00L,
            prevExpenseCents = 40_00L,
            categoryNoteSamples = listOf(
                CategoryNoteSample(1L, "餐饮", listOf("食堂午餐", "奶茶")),
                CategoryNoteSample(null, null, listOf("忘了记分类")),
            ),
        )
        assertTrue(text.contains("各类代表备注"))
        assertTrue(text.contains("食堂午餐"))
        assertTrue(text.contains("未分类"))
        assertTrue(text.contains("忘了记分类"))
    }

    @Test
    fun `system instruction mentions notes`() {
        val instruction = ReportPromptBuilder.buildSystemInstruction()
        assertTrue(instruction.contains("代表备注"))
        assertTrue(instruction.contains("500"))
    }

    @Test
    fun `note section respects hard char budget`() {
        val longNote = "字".repeat(900)
        val text = ReportPromptBuilder.buildDataText(
            incomeCents = 0L,
            expenseCents = 10_00L,
            topCategories = emptyList(),
            prevIncomeCents = 0L,
            prevExpenseCents = 0L,
            categoryNoteSamples = listOf(
                CategoryNoteSample(1L, "学习", listOf(longNote)),
                CategoryNoteSample(2L, "生活", listOf("不应出现若预算已耗尽-标记XYZ")),
            ),
        )
        assertTrue(text.contains("各类代表备注"))
        assertTrue(text.contains("学习"))
        // 800 字硬顶：第二条分类不应完整出现标记
        val noteBlock = text.substringAfter("各类代表备注")
        assertTrue(noteBlock.length < 900)
    }
}
