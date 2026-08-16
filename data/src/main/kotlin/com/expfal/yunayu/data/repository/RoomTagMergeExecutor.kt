package com.expfal.yunayu.data.repository

import androidx.room.withTransaction
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TagMergeExecutor] 的 Room 实现：经 [YunayuDatabase.withTransaction] 保证「先迁移后删除」原子性。
 *
 * 迁移与删除两步必须落在同一事务：若先删除 [dropTagId]，`transactions.tag_id` 外键
 * `ON DELETE SET_NULL` 会把迁移目标交易置空，合并语义被破坏；若两者分属两事务，则中间态
 * 可见且失败无法回滚。
 */
@Singleton
class RoomTagMergeExecutor @Inject constructor(
    private val database: YunayuDatabase,
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
) : TagMergeExecutor {

    /** 在单事务内迁移 [dropTagId] 交易到 [keepTagId] 后删除 [dropTagId]。 */
    override suspend fun merge(keepTagId: Long, dropTagId: Long) {
        database.withTransaction {
            transactionDao.updateTagIdByTagIds(listOf(dropTagId), keepTagId)
            tagDao.deleteById(dropTagId)
        }
    }
}
