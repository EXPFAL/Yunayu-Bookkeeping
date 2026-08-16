package com.expfal.yunayu.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.data.repository.RoomTagMergeExecutor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [RoomTagMergeExecutor] 的真库合并行为验证（androidTest，in-memory DB）。
 *
 * 验证「先迁移后删除」的原子语义：迁移后无孤儿交易，drop 标签被删除，keep 标签下交易汇聚。
 */
@RunWith(AndroidJUnit4::class)
class RoomTagMergeExecutorTest {

    private lateinit var database: YunayuDatabase
    private lateinit var executor: RoomTagMergeExecutor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            YunayuDatabase::class.java,
        ).build()
        executor = RoomTagMergeExecutor(database, database.transactionDao(), database.tagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun merge_migratesTransactionsThenDeletesDropTag() = runBlocking {
        val tagDao = database.tagDao()
        val txDao = database.transactionDao()
        val rootId = tagDao.insert(tag("学习", parentId = null))
        val foodId = tagDao.insert(tag("餐饮", parentId = rootId))
        val eatId = tagDao.insert(tag("吃饭", parentId = rootId))
        txDao.insert(transaction(10L, foodId, 1_000L))
        txDao.insert(transaction(20L, foodId, 2_000L))
        txDao.insert(transaction(30L, eatId, 3_000L))

        executor.merge(keepTagId = foodId, dropTagId = eatId)

        // drop 标签被删除，其下交易迁移至 keep，无孤儿交易
        assertTrue(tagDao.getAll().none { it.id == eatId })
        assertEquals(3, txDao.getOccurredAtsByTagIds(listOf(foodId)).size)
        assertTrue(txDao.getOccurredAtsByTagIds(listOf(eatId)).isEmpty())
    }

    private fun tag(name: String, parentId: Long?): TagEntity = TagEntity(
        name = name,
        parentId = parentId,
        sortOrder = 0,
        icon = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    private fun transaction(amountCents: Long, tagId: Long?, occurredAt: Long): TransactionEntity =
        TransactionEntity(
            amountCents = amountCents,
            type = "EXPENSE",
            note = null,
            tagId = tagId,
            occurredAt = occurredAt,
            createdAt = System.currentTimeMillis(),
        )
}
