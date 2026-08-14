package com.expfal.yunayu.domain.model

/**
 * 删除标签的影响面快照（PRD P0-3 删除确认弹窗数据源）。
 *
 * @property subtreeNodeCount 待删除子树节点数（含被删除标签自身）。
 * @property affectedTransactionCount 将被置空标签（`tag_id → NULL`）影响的交易数。
 * @property subtreeNames 子树内各标签名（BFS 序），用于删除确认文案展示。
 */
data class TagDeleteImpact(
    val subtreeNodeCount: Int,
    val affectedTransactionCount: Int,
    val subtreeNames: List<String>,
)
