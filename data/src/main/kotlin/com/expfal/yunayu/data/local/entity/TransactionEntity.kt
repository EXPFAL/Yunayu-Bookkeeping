package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 交易表。金额一律以「分」为单位（Long），避免浮点误差。
 *
 * `tagId` 为可空学业标签外键（SCAFFOLD.md 4.3 方案 A：单笔交易最多挂一个学业标签），
 * 标签删除时置空而非级联删除交易。
 *
 * Schema v2：新增复合索引 `(occurred_at, type)`，服务预算聚合查询形态。
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tag_id"), Index("occurred_at"), Index(value = ["occurred_at", "type"])],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "tag_id") val tagId: Long?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
