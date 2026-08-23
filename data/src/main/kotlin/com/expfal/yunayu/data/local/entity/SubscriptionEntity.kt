package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 订阅开支表（Schema v9）。金额以分存储；[billing_cycle] 存 [com.expfal.yunayu.domain.model.SubscriptionBillingCycle] 枚举名。 */
@Entity(
    tableName = "subscriptions",
    indices = [Index("is_active")],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "billing_cycle") val billingCycle: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "billing_start_at") val billingStartAt: Long,
    @ColumnInfo(name = "last_posted_due_at") val lastPostedDueAt: Long?,
    @ColumnInfo(name = "last_posted_at") val lastPostedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
