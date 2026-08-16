package com.expfal.yunayu.domain.model

/**
 * 预置账户名单一数据源。
 *
 * 迁移 [com.expfal.yunayu.data.local.YunayuDatabase.MIGRATION_4_5] 的建库种子与启动补齐
 * （[com.expfal.yunayu.domain.usecase.EnsureAccountsUseCase]）统一引用本处常量，避免账户名在
 * 多处硬编码漂移。列表顺序即迁移种子插入顺序。
 */
object AccountPresets {

    /** 预置账户名（微信 / 支付宝 / 银行卡）。 */
    val PRESET_NAMES = listOf("微信", "支付宝", "银行卡")
}
