package com.expfal.yunayu.domain.model

/**
 * 学业关联标签（PRD P0-3）。
 *
 * 标签为树形结构：`parentId == null` 表示根节点，内置「学习/社交/生活/娱乐」四大类；
 * 自定义子标签（教材/考证/实习等）挂在根节点之下，同级按 `sortOrder` 递增排序。
 */
data class Tag(
    val id: Long = 0L,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val icon: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
