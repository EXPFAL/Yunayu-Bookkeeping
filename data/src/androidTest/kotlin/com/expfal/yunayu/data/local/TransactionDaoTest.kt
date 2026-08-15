package com.expfal.yunayu.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expfal.yunayu.data.local.dao.TransactionDao
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

    private fun transaction(amountCents: Long, type: String): TransactionEntity = TransactionEntity(
        amountCents = amountCents,
        type = type,
        note = null,
        tagId = null,
        occurredAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
    )
}
