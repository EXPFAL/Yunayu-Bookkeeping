package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.expfal.yunayu.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/** 订阅开支 DAO。 */
@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions ORDER BY is_active DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Insert
    suspend fun insert(entity: SubscriptionEntity): Long

    @Update
    suspend fun update(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
