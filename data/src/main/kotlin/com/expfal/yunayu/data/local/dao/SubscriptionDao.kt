package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/** 订阅表基础 DAO（schema 对齐用；业务 UI 后续再接）。 */
@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun count(): Long
}
