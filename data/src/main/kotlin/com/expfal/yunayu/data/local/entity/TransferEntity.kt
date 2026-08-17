package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 转账表。金额一律以「分」为单位（Long）。
 *
 * [fromAccountId] / [toAccountId] 均非空外键指向 accounts.id，删除账户时级联删除
 * 涉及该账户的转账（与 transactions.account_id 的 ON DELETE SET NULL 语义不同：
 * 转账离开账户后即失去意义）。
 *
 * Schema v6：新增本表 + occurred_at / from_account_id / to_account_id 三索引。
 */
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("occurred_at"),
        Index("from_account_id"),
        Index("to_account_id"),
    ],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "from_account_id") val fromAccountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
