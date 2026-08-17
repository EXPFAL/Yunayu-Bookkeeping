package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账户表（资金渠道）。名称为唯一索引，同名账户不允许重复。
 *
 * Schema v5：新增本表 + 唯一索引 `name`；transactions 经 `account_id` 外键引用本表
 * （ON DELETE SET NULL，删除账户时交易置为「未指定账户」而非级联删除）。
 * Schema v6：新增 `initial_balance_cents` 期初余额列（非空默认 0）。
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "initial_balance_cents", defaultValue = "0") val initialBalanceCents: Long = 0L,
)
