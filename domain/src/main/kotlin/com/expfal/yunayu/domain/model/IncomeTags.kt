package com.expfal.yunayu.domain.model

/**
 * 独立收入标签体系的单一数据源。
 *
 * 收入根类与支出四大根类（学习/社交/生活/娱乐）分属两套体系，互不干扰。种子化
 * （[EnsureIncomeTagsUseCase]）、收支过滤（快捷记账选择层 / 最近分类回退）与 LLM 白名单
 * （[TagRepository.addRootTag]）统一引用本处常量，避免「收入」根名 / 子标签 / 图标在多处
 * 硬编码漂移。
 */
object IncomeTags {

    /** 收入根类名（预置白名单唯一根名）。 */
    const val INCOME_ROOT_NAME = "收入"

    /** 收入根类图标，与既有根类 emoji 图标风格一致。 */
    const val INCOME_ROOT_ICON = "💰"

    /** 收入种子子标签（列表顺序即 sortOrder 递增）。 */
    val INCOME_SEED_SUB_TAGS = listOf("生活费", "还款", "AA收款", "理财收益", "兼职经营", "其他收入")
}
