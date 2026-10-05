package com.expfal.yunayu.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ExpenseSeedTags] 新库生活叶子清单。 */
class ExpenseSeedTagsTest {

    @Test
    fun `new life seed has stockpile dining-out and business stock`() {
        val life = ExpenseSeedTags.SEED_SUB_TAGS.getValue(ExpenseSeedTags.LIFE_ROOT_NAME)
        assertEquals(
            listOf("囤货三餐", "外出就餐", "饮品", "交通", "购物", "生活缴费", "医疗", "经营进货"),
            life,
        )
        assertFalse("餐饮" in life)
        assertFalse("洗衣" in life)
        assertFalse("水果" in life)
    }

    @Test
    fun `ensure list is the three incremental life leaves`() {
        assertEquals(
            listOf("囤货三餐", "外出就餐", "经营进货"),
            ExpenseSeedTags.ENSURE_LIFE_CHILDREN,
        )
    }

    @Test
    fun `other roots keep previous seed children`() {
        assertEquals(
            listOf("课本教辅", "考证", "实习", "订阅"),
            ExpenseSeedTags.SEED_SUB_TAGS.getValue(ExpenseSeedTags.STUDY_ROOT_NAME),
        )
        assertEquals(listOf("聚餐"), ExpenseSeedTags.SEED_SUB_TAGS.getValue(ExpenseSeedTags.SOCIAL_ROOT_NAME))
        assertEquals(
            listOf("游戏", "运动", "出游", "骑行"),
            ExpenseSeedTags.SEED_SUB_TAGS.getValue(ExpenseSeedTags.FUN_ROOT_NAME),
        )
        assertTrue(ExpenseSeedTags.ROOT_TAGS.map { it.first }.containsAll(
            listOf("学习", "社交", "生活", "娱乐"),
        ))
    }
}
