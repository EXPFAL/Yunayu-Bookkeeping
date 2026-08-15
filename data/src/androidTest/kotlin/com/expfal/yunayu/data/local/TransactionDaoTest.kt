package com.expfal.yunayu.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TransactionDao] 的聚合 SQL 行为验证（androidTest）。
 *
 * 注意：本测试依赖 Android 框架 SQLite，需在设备/模拟器上执行；本机无模拟器/设备，
 * 故写好但不在本机运行，接入 CI 后启用。
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: YunayuDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            YunayuDatabase::class.java,
        ).build()
        dao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun heldCents_isNetOfIncomeMinusExpense_andExpenseSumIgnoresIncome() = runBlocking {
        dao.insert(transaction(amountCents = 10_000L, type = "INCOME"))
        dao.insert(transaction(amountCents = 3_000L, type = "EXPENSE"))
        dao.insert(transaction(amountCents = 2_000L, type = "EXPENSE"))

        // 净额 = 收入 10000 − 支出(3000+2000) = 5000
        assertEquals(5_000L, dao.observeHeldCents().first())

        // 支出总额窗口覆盖全历史，应忽略 INCOME 行，仅统计两笔 EXPENSE
        assertEquals(5_000L, dao.observeExpenseSumBetween(0L, Long.MAX_VALUE).first())
    }

    @Test
    fun observeFiltered_allNull_returnsAllDescendingByOccurredAt() = runBlocking {
        dao.insert(transaction(amountCents = 100L, type = "EXPENSE", note = "早", occurredAt = 1_000L))
        dao.insert(transaction(amountCents = 200L, type = "EXPENSE", note = "午", occurredAt = 2_000L))
        dao.insert(transaction(amountCents = 300L, type = "EXPENSE", note = "晚", occurredAt = 3_000L))

        val rows = dao.observeFiltered(null, null, null).first()

        // 参数全 null：不过滤，按 occurred_at 倒序返回全量
        assertEquals(3, rows.size)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), rows.map { it.transaction.occurredAt })
    }

    @Test
    fun observeFiltered_windowHalfOpenBounds_andKeywordCombined() = runBlocking {
        dao.insert(transaction(amountCents = 100L, type = "EXPENSE", note = "食堂午餐", occurredAt = 1_000L))
        dao.insert(transaction(amountCents = 200L, type = "EXPENSE", note = "食堂晚餐", occurredAt = 2_000L))
        dao.insert(transaction(amountCents = 300L, type = "EXPENSE", note = "食堂夜宵", occurredAt = 3_000L))
        dao.insert(transaction(amountCents = 400L, type = "EXPENSE", note = "外卖", occurredAt = 4_000L))

        // 半开区间 [1_000, 3_000)：start 边界含 1_000、2_000；end 边界 3_000 排除
        val windowed = dao.observeFiltered(1_000L, 3_000L, null).first()
        assertEquals(listOf(2_000L, 1_000L), windowed.map { it.transaction.occurredAt })

        // 时间窗 + 关键词组合：窗口内仅「食堂晚餐」命中
        val combined = dao.observeFiltered(1_000L, 3_000L, "晚餐").first()
        assertEquals(listOf(2_000L), combined.map { it.transaction.occurredAt })

        // 关键词无命中：窗口不变时返回空
        assertEquals(0, dao.observeFiltered(1_000L, 3_000L, "不存在的词").first().size)

        // start 边界（含）与 end 边界（不含）单独验证
        val startBoundary = dao.observeFiltered(1_000L, 2_000L, null).first()
        assertEquals(listOf(1_000L), startBoundary.map { it.transaction.occurredAt })
    }

    @Test
    fun observeFiltered_likeKeywordEscaped_matchesLiteralPercentAndUnderscore() = runBlocking {
        dao.insert(transaction(amountCents = 1L, type = "EXPENSE", note = "100%_完成", occurredAt = 1_000L))
        dao.insert(transaction(amountCents = 2L, type = "EXPENSE", note = "100%完成", occurredAt = 2_000L))
        dao.insert(transaction(amountCents = 3L, type = "EXPENSE", note = "100a完成", occurredAt = 3_000L))
        dao.insert(transaction(amountCents = 4L, type = "EXPENSE", note = "午饭100%_套餐", occurredAt = 4_000L))

        // 转义产物形态与 TransactionRepositoryImpl.escapeLikeKeyword 一致：
        // \ → \\、% → \% 、_ → \_，DAO 层直接传已转义串（SQL 侧 ESCAPE '\'）
        val escaped = "100\\%\\_"
        val rows = dao.observeFiltered(null, null, escaped).first()

        // 精确匹配字面 "100%_"：仅命中两处字面串，通配干扰项（100%完成 / 100a完成）不命中
        assertEquals(2, rows.size)
        assertEquals(
            listOf("午饭100%_套餐", "100%_完成"),
            rows.map { it.transaction.note },
        )

        // 未转义关键词会触发 %/_ 通配命中全部 4 条，佐证转义必要性
        assertEquals(4, dao.observeFiltered(null, null, "100%_").first().size)
    }

    @Test
    fun observeFilteredByTags_multiTagIn_combinedWithWindowAndKeyword() = runBlocking {
        val tagDao = database.tagDao()
        val foodId = tagDao.insert(tag("餐饮"))
        val bookId = tagDao.insert(tag("书本"))
        val gameId = tagDao.insert(tag("游戏"))

        dao.insert(transaction(amountCents = 10L, type = "EXPENSE", note = "食堂", occurredAt = 1_000L, tagId = foodId))
        dao.insert(transaction(amountCents = 20L, type = "EXPENSE", note = "课本", occurredAt = 2_000L, tagId = bookId))
        dao.insert(transaction(amountCents = 30L, type = "EXPENSE", note = "食堂", occurredAt = 3_000L, tagId = gameId))
        dao.insert(transaction(amountCents = 40L, type = "EXPENSE", note = "未分类", occurredAt = 4_000L, tagId = null))

        // 多标签 IN（跨餐饮/书本）：命中两笔，未分类与游戏标签行被排除
        val byTags = dao.observeFilteredByTags(null, null, null, listOf(foodId, bookId)).first()
        assertEquals(setOf(1_000L, 2_000L), byTags.map { it.transaction.occurredAt }.toSet())

        // 与时间窗组合：窗口 [2_000, MAX) 内仅剩书本那笔
        val windowed = dao.observeFilteredByTags(2_000L, Long.MAX_VALUE, null, listOf(foodId, bookId)).first()
        assertEquals(listOf(2_000L), windowed.map { it.transaction.occurredAt })

        // 与关键词组合：三标签全选时关键词「食堂」命中餐饮与游戏两笔
        val keyword = dao.observeFilteredByTags(null, null, "食堂", listOf(foodId, bookId, gameId)).first()
        assertEquals(setOf(1_000L, 3_000L), keyword.map { it.transaction.occurredAt }.toSet())
    }

    @Test
    fun deleteById_emitsUpdatedFlows_withoutDeletedRecord() = runBlocking {
        val keepId = dao.insert(transaction(amountCents = 100L, type = "EXPENSE", note = "保留", occurredAt = 1_000L))
        dao.insert(transaction(amountCents = 200L, type = "EXPENSE", note = "待删", occurredAt = 2_000L))

        assertEquals(2, dao.observeFiltered(null, null, null).first().size)
        assertEquals(2, dao.observeRecent(10).first().size)

        dao.deleteById(keepId)

        // observeFiltered 重发且不再含被删记录
        val filteredAfter = dao.observeFiltered(null, null, null)
            .first { rows -> rows.none { it.transaction.id == keepId } }
        assertEquals(1, filteredAfter.size)
        assertEquals(2_000L, filteredAfter.single().transaction.occurredAt)

        // observeRecent 重发且不再含被删记录
        val recentAfter = dao.observeRecent(10)
            .first { rows -> rows.none { it.transaction.id == keepId } }
        assertEquals(1, recentAfter.size)
        assertEquals(2_000L, recentAfter.single().transaction.occurredAt)
    }

    private fun transaction(
        amountCents: Long,
        type: String,
        note: String? = null,
        tagId: Long? = null,
        occurredAt: Long = System.currentTimeMillis(),
    ): TransactionEntity = TransactionEntity(
        amountCents = amountCents,
        type = type,
        note = note,
        tagId = tagId,
        occurredAt = occurredAt,
        createdAt = System.currentTimeMillis(),
    )

    private fun tag(name: String): TagEntity = TagEntity(
        name = name,
        parentId = null,
        sortOrder = 0,
        icon = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )
}
