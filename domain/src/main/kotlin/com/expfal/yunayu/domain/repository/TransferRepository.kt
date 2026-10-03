package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Transfer
import kotlinx.coroutines.flow.Flow

/** 转账仓储接口，由 :data 模块实现。 */
interface TransferRepository {

    /** 观察全部转账，按发生时间倒序。 */
    fun observeTransfers(): Flow<List<Transfer>>

    /** 按主键查询转账；不存在返回 `null`。 */
    suspend fun getById(id: Long): Transfer?

    /** 新增一笔转账，返回其主键。 */
    suspend fun insertTransfer(transfer: Transfer): Long

    /**
     * 更新一笔转账（金额 / 转出转入 / 备注 / 发生时间），保留原 `createdAt`。
     * 目标不存在时为无操作。
     */
    suspend fun updateTransfer(transfer: Transfer)

    /** 删除一笔转账（按主键）。 */
    suspend fun deleteById(id: Long)
}
