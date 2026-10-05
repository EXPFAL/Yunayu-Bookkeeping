package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.ExpenseSeedTags
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.report.LifestyleHeuristics.LifestyleKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [LifestyleHeuristics] 纯函数单元测试。 */
class LifestyleHeuristicsTest {

    @Test
    fun `explicit tags beat notes`() {
        assertEquals(
            LifestyleKind.STOCKPILE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, null, ExpenseSeedTags.TAG_STOCKPILE),
        )
        assertEquals(
            LifestyleKind.STOCKPILE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, null, ExpenseSeedTags.TAG_FRUIT_LEGACY),
        )
        assertEquals(
            LifestyleKind.DINING_OUT,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "买配件", ExpenseSeedTags.TAG_DINING_OUT),
        )
        assertEquals(
            LifestyleKind.BUSINESS_EXPENSE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "泡面", ExpenseSeedTags.TAG_BUSINESS_STOCK),
        )
        assertEquals(
            LifestyleKind.DINING_OUT,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, null, ExpenseSeedTags.TAG_GATHERING),
        )
    }

    @Test
    fun `shopping tag with parts note is not business`() {
        assertEquals(
            LifestyleKind.OTHER,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "自行车配件", "购物"),
        )
    }

    @Test
    fun `legacy catering tag still uses note fallback`() {
        assertEquals(
            LifestyleKind.STOCKPILE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "买罐头泡面", "餐饮"),
        )
        assertEquals(
            LifestyleKind.DINING_OUT,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "食堂午餐", "餐饮"),
        )
    }

    @Test
    fun `untagged notes still classify`() {
        assertEquals(
            LifestyleKind.STOCKPILE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "囤货速食", null),
        )
        assertEquals(
            LifestyleKind.BUSINESS_EXPENSE,
            LifestyleHeuristics.classify(TransactionType.EXPENSE, "修车内胎配件", null),
        )
    }

    @Test
    fun `business income uses jianzhi jingying tag`() {
        assertEquals(
            LifestyleKind.BUSINESS_INCOME,
            LifestyleHeuristics.classify(TransactionType.INCOME, "修车费", "兼职经营"),
        )
        assertEquals(
            LifestyleKind.OTHER,
            LifestyleHeuristics.classify(TransactionType.INCOME, "生活费到账", "生活费"),
        )
    }
}
