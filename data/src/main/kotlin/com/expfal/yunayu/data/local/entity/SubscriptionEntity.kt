package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订阅表（周期扣费备忘）。手机端历史 schema v8/v9 已落库；本实体对齐存量列，避免 Room 打开失败。
 *
 * Schema v10：正式纳入本仓库实体集（与手机 user_version=9 表结构一致，并叠加 reports.local_insights）。
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["is_active"])],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "billing_cycle") val billingCycle: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "billing_start_at", defaultValue = "0") val billingStartAt: Long = 0L,
    @ColumnInfo(name = "last_posted_at") val lastPostedAt: Long? = null,
    @ColumnInfo(name = "last_posted_due_at") val lastPostedDueAt: Long? = null,
)
