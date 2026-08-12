package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学业关联标签表（PRD P0-3，schema 见 SCAFFOLD.md 4.1/4.2）。
 *
 * `parentId == null` 表示根节点；四大类「学习/社交/生活/娱乐」在建库时种子化为根节点。
 */
@Entity(
    tableName = "tags",
    indices = [Index("parent_id"), Index(value = ["parent_id", "sort_order"])],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "icon") val icon: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
