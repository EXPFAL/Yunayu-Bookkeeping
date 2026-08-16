package com.expfal.yunayu.domain.model

/**
 * 账户（资金渠道）。用于回答「钱在哪个渠道」的真实问题，将单一资金池拆分为微信 / 支付宝 /
 * 银行卡等渠道余额。
 *
 * 预置账户名见 [AccountPresets.PRESET_NAMES]（迁移种子与启动补齐的单一数据源），
 * 用户亦可自定义增删改。
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = 0L,
)
