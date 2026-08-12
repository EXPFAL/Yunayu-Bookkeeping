package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学业关联标签表（PRD P0-3，schema 见 SCAFFOLD.md 4.1/4.2 与「Schema v2 增强记录」）。
 *
 * `parentId == null` 表示根节点；四大类「学习/社交/生活/娱乐」在建库时种子化为根节点。
 *
 * Schema v2：新增自引用外键 `parent_id → id`（ON DELETE CASCADE，删父级联删子树），
 * 唯一索引 `(parent_id, name)`（同父节点下不允许重名）。
 */
@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["parent_id", "name"], unique = true),
        Index(value = ["parent_id", "sort_order"]),
    ],
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
