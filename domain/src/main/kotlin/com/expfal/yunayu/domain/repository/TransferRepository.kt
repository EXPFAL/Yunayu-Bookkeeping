package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Transfer
import kotlinx.coroutines.flow.Flow

/** 转账仓储接口，由 :data 模块实现。 */
interface TransferRepository {

    /** 观察全部转账，按发生时间倒序。 */
    fun observeTransfers(): Flow<List<Transfer>>

    /** 新增一笔转账，返回其主键。 */
    suspend fun insertTransfer(transfer: Transfer): Long

    /** 删除一笔转账（按主键）。 */
    suspend fun deleteById(id: Long)
}
