package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/** 交易仓储接口，由 :data 模块实现。 */
interface TransactionRepository {

    /** 新增一笔交易，返回其主键。 */
    suspend fun add(transaction: Transaction): Long

    /** 观察全部交易，按发生时间倒序。 */
    fun observeAll(): Flow<List<Transaction>>

    /** 观察挂在指定学业标签下的交易，按发生时间倒序。 */
    fun observeByTag(tagId: Long): Flow<List<Transaction>>
}
