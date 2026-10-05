package com.expfal.yunayu.domain.model

/**
 * 支出四大根类及其种子子标签的单一数据源。
 *
 * 与 [IncomeTags] 分属两套体系。首次建库（[com.expfal.yunayu.data.local.YunayuDatabase] 种子）
 * 与存量补齐（[com.expfal.yunayu.domain.usecase.EnsureExpenseTagsUseCase]）共用本处常量。
 */
object ExpenseSeedTags {

    const val STUDY_ROOT_NAME = "学习"
    const val SOCIAL_ROOT_NAME = "社交"
    const val LIFE_ROOT_NAME = "生活"
    const val FUN_ROOT_NAME = "娱乐"

    const val TAG_STOCKPILE = "囤货三餐"
    const val TAG_DINING_OUT = "外出就餐"
    const val TAG_BUSINESS_STOCK = "经营进货"
    const val TAG_GATHERING = "聚餐"
    const val TAG_FRUIT_LEGACY = "水果"

    /** 四大根类（名、图标），列表顺序即 sortOrder。 */
    val ROOT_TAGS = listOf(
        STUDY_ROOT_NAME to "📚",
        SOCIAL_ROOT_NAME to "🤝",
        LIFE_ROOT_NAME to "🏠",
        FUN_ROOT_NAME to "🎮",
    )

    /**
     * 新库种子子标签（根类名 → 叶子，列表顺序即 sortOrder）。
     * 生活类已去掉餐饮/洗衣/水果，改为囤货三餐、外出就餐、经营进货。
     */
    val SEED_SUB_TAGS = mapOf(
        STUDY_ROOT_NAME to listOf("课本教辅", "考证", "实习", "订阅"),
        SOCIAL_ROOT_NAME to listOf(TAG_GATHERING),
        LIFE_ROOT_NAME to listOf(
            TAG_STOCKPILE,
            TAG_DINING_OUT,
            "饮品",
            "交通",
            "购物",
            "生活缴费",
            "医疗",
            TAG_BUSINESS_STOCK,
        ),
        FUN_ROOT_NAME to listOf("游戏", "运动", "出游", "骑行"),
    )

    /** 存量库只补这些生活叶子（不删旧的餐饮/洗衣/水果）。 */
    val ENSURE_LIFE_CHILDREN = listOf(TAG_STOCKPILE, TAG_DINING_OUT, TAG_BUSINESS_STOCK)
}
