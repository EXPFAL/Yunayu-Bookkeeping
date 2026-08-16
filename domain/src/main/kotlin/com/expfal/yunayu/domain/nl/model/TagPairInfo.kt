package com.expfal.yunayu.domain.nl.model

/**
 * 合并候选提示词中的一对标签信息：两个标签名及其各自直接挂载的交易记录数。
 *
 * 仅携带提示词构建所需的最小字段，`nameA` / `nameB` 取叶子标签裸名（非「根·子」全名），
 * 供引擎判定语义重复；`countA` / `countB` 辅助引擎理解标签使用量。
 */
data class TagPairInfo(
    val nameA: String,
    val nameB: String,
    val countA: Int,
    val countB: Int,
)
