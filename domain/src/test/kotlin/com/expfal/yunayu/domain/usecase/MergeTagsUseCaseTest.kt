package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [MergeTagsUseCase] 的 JVM 单元测试（手写 fake 三仓储）。 */
class MergeTagsUseCaseTest {

    @Test
    fun `invalidates reports for distinct occurred at and returns migrated count`() = runTest {
        val tags = FakeTagRepository()
        val tx = FakeTransactionRepository().apply {
            occurredAts = listOf(1_000L, 2_000L, 1_000L)
        }
        val reports = FakeReportRepository()
        val useCase = MergeTagsUseCase(tags, tx, reports)

        val result = useCase(keepTagId = 2L, dropTagId = 3L)

        // 去重后按 [1_000, 2_000] 顺序标脏；受影响交易数按合并前快照（含重复）计
        assertEquals(listOf(1_000L, 2_000L), reports.invalidated)
        assertEquals(3, result.affectedTransactionCount)
        assertEquals(listOf(2L to 3L), tags.mergeCalls)
        assertEquals(listOf(listOf(3L)), tx.occurredAtsCalls)
    }

    @Test
    fun `propagates merge failure`() = runTest {
        val tags = FakeTagRepository().apply { mergeError = IllegalArgumentException("仅叶子标签可合并") }
        val tx = FakeTransactionRepository().apply { occurredAts = listOf(1_000L) }
        val reports = FakeReportRepository()
        val useCase = MergeTagsUseCase(tags, tx, reports)

        val error = runCatching { useCase(keepTagId = 2L, dropTagId = 3L) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)

        // 合并失败：不应标脏报告
        assertTrue(reports.invalidated.isEmpty())
    }

    @Test
    fun `does not invalidate reports for empty occurred at`() = runTest {
        val tags = FakeTagRepository()
        val tx = FakeTransactionRepository().apply { occurredAts = emptyList() }
        val reports = FakeReportRepository()
        val useCase = MergeTagsUseCase(tags, tx, reports)

        val result = useCase(keepTagId = 2L, dropTagId = 3L)

        assertEquals(0, result.affectedTransactionCount)
        assertTrue(reports.invalidated.isEmpty())
        assertEquals(listOf(2L to 3L), tags.mergeCalls)
    }

    /** [TagRepository] 手写 fake：记录 mergeTags 入参，可配置合并异常。 */
    private class FakeTagRepository : TagRepository {

        val mergeCalls = mutableListOf<Pair<Long, Long>>()
        var mergeError: Throwable? = null

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> = emptyList()

        override suspend fun getRecentUsedTags(
            sinceEpochMillis: Long,
            type: TransactionType,
            limit: Int,
        ): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
            TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) {
            mergeError?.let { throw it }
            mergeCalls += keepTagId to dropTagId
        }
    }

    /** [TransactionRepository] 手写 fake：记录 getOccurredAtsByTagIds 入参，返回预置 occurredAt。 */
    private class FakeTransactionRepository : TransactionRepository {

        var occurredAts: List<Long> = emptyList()
        val occurredAtsCalls = mutableListOf<List<Long>>()

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): Flow<Long> = flowOf(0L)

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> {
            occurredAtsCalls += tagIds
            return occurredAts
        }

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    /** [ReportRepository] 手写 fake：记录标脏入参。 */
    private class FakeReportRepository : ReportRepository {

        val invalidated = mutableListOf<Long>()

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidated += epochMillis
        }
    }
}
