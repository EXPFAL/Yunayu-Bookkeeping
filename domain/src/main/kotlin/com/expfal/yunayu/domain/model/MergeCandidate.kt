package com.expfal.yunayu.domain.model

/**
 * 标签合并决策方向。
 *
 * 与合并候选引擎输出的 JSON `decision` 字段一一对应：`A_INTO_B` / `B_INTO_A` 表示两标签
 * 语义重复应合并（前者丢弃 A 保留 B，后者相反）；`KEEP_BOTH` 表示两者语义不同应各自保留。
 */
enum class MergeDecision {
    KEEP_BOTH,
    MERGE_A_INTO_B,
    MERGE_B_INTO_A,
}

/**
 * 一条标签合并候选：语义重复的一对叶子标签及其交易记录数。
 *
 * @property tagA 候选对中的标签 A（对应引擎输出的 `tag_a`）。
 * @property tagB 候选对中的标签 B（对应引擎输出的 `tag_b`）。
 * @property countA 标签 A 直接挂载的交易记录数。
 * @property countB 标签 B 直接挂载的交易记录数。
 * @property decision 合并决策方向；[MergeDecision.MERGE_A_INTO_B] 表示丢弃 A 保留 B，
 *   [MergeDecision.MERGE_B_INTO_A] 表示丢弃 B 保留 A。
 */
data class MergeCandidate(
    val tagA: Tag,
    val tagB: Tag,
    val countA: Int,
    val countB: Int,
    val decision: MergeDecision,
)
