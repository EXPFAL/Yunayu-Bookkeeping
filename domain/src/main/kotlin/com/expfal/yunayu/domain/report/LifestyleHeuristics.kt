package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.ExpenseSeedTags
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.TransactionType

/**
 * 校园生活启发式：标签名优先，备注仅在未定性时兜底。
 *
 * 纯函数、无 IO。一笔只归一类。已挂明确支出叶子（如购物、骑行）时不按备注改判，
 * 避免「购物 + 配件」被当成经营进货。
 */
object LifestyleHeuristics {

    enum class LifestyleKind {
        STOCKPILE,
        DINING_OUT,
        BUSINESS_EXPENSE,
        BUSINESS_INCOME,
        OTHER,
    }

    private val STOCKPILE_KEYWORDS = listOf(
        "罐头",
        "泡面",
        "方便面",
        "速食",
        "自热",
        "螺蛳粉",
        "囤货",
    )

    private val DINING_OUT_KEYWORDS = listOf(
        "食堂",
        "餐厅",
        "餐馆",
        "聚餐",
        "外卖",
        "堂食",
        "火锅",
        "烧烤",
    )

    private val BUSINESS_EXPENSE_KEYWORDS = listOf(
        "配件",
        "内胎",
        "外胎",
        "链条",
        "飞轮",
        "补胎",
        "修车",
        "扳手",
        "螺丝刀",
    )

    private val BUSINESS_INCOME_TAG = IncomeTags.INCOME_SEED_SUB_TAGS.first { it == "兼职经营" }

    /** 存量「餐饮」仍可用备注拆囤货/外出；空标签同样走备注。 */
    private val NOTE_FALLBACK_TAGS = setOf(null, "", "餐饮")

    fun classify(
        type: TransactionType,
        note: String?,
        tagName: String?,
    ): LifestyleKind {
        if (type == TransactionType.INCOME) {
            return if (tagName == BUSINESS_INCOME_TAG) {
                LifestyleKind.BUSINESS_INCOME
            } else {
                LifestyleKind.OTHER
            }
        }
        when (tagName) {
            ExpenseSeedTags.TAG_BUSINESS_STOCK -> return LifestyleKind.BUSINESS_EXPENSE
            ExpenseSeedTags.TAG_STOCKPILE, ExpenseSeedTags.TAG_FRUIT_LEGACY ->
                return LifestyleKind.STOCKPILE
            ExpenseSeedTags.TAG_DINING_OUT, ExpenseSeedTags.TAG_GATHERING ->
                return LifestyleKind.DINING_OUT
        }
        if (tagName !in NOTE_FALLBACK_TAGS) {
            return LifestyleKind.OTHER
        }
        val noteText = note.orEmpty()
        if (containsAny(noteText, BUSINESS_EXPENSE_KEYWORDS)) {
            return LifestyleKind.BUSINESS_EXPENSE
        }
        if (containsAny(noteText, STOCKPILE_KEYWORDS)) {
            return LifestyleKind.STOCKPILE
        }
        if (containsAny(noteText, DINING_OUT_KEYWORDS)) {
            return LifestyleKind.DINING_OUT
        }
        return LifestyleKind.OTHER
    }

    private fun containsAny(haystack: String, keywords: List<String>): Boolean {
        if (haystack.isEmpty()) return false
        return keywords.any { haystack.contains(it, ignoreCase = true) }
    }
}
