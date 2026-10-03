package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

/** 订阅开支仓储：维护用户录入的长期订阅项，供总览与月均摊计算。 */
interface SubscriptionRepository {

    /** 观察全部订阅项，生效项优先、同态按名称排序。 */
    fun observeAll(): Flow<List<Subscription>>

    /** 按 id 获取订阅项。 */
    suspend fun getById(id: Long): Subscription?

    /** 新增订阅项，返回新 id。 */
    suspend fun add(subscription: Subscription): Long

    /** 更新订阅项（含暂停/恢复、推进到期日）。 */
    suspend fun update(subscription: Subscription)

    /** 按 id 删除订阅项。 */
    suspend fun delete(id: Long)
}
