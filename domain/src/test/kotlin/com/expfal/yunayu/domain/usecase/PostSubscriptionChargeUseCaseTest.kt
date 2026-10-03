package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.SubscriptionBillingCycle
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.model.advanceDueAt
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class PostSubscriptionChargeUseCaseTest {

    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `posts expense and records posted due date without changing start date`() = runTest {
        val startAt = LocalDate.of(2026, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val subscription = Subscription(
            id = 1L,
            name = "Netflix",
            amountCents = 3_000L,
            billingCycle = SubscriptionBillingCycle.MONTHLY,
            billingStartAt = startAt,
        )
        val subscriptionRepo = FakeSubscriptionRepository(subscription)
        val tagRepo = FakeTagRepository(studyRootId = 10L, subscriptionTagId = 11L)
        val transactionRepo = FakeTransactionRepository()
        val useCase = PostSubscriptionChargeUseCase(
            subscriptionRepo,
            tagRepo,
            AddTransactionUseCase(transactionRepo, FakeReportRepository()),
        )

        val result = useCase(1L, accountId = 9L)

        assertTrue(result is PostSubscriptionChargeResult.Success)
        val tx = transactionRepo.added.single()
        assertEquals(9L, tx.accountId)
        assertEquals(3_000L, tx.amountCents)
        assertEquals(11L, tx.tagId)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(startAt, tx.occurredAt)
        assertTrue(tx.note!!.contains("Netflix"))
        val updated = subscriptionRepo.lastUpdated!!
        assertEquals(startAt, updated.billingStartAt)
        assertEquals(startAt, updated.lastPostedDueAt)
        assertEquals(
            SubscriptionBillingCycle.MONTHLY.advanceDueAt(startAt, zoneId),
            updated.nextChargeDueAt(zoneId),
        )
        assertTrue(updated.lastPostedAt != null)
    }

    @Test
    fun `returns not found when subscription missing`() = runTest {
        val useCase = PostSubscriptionChargeUseCase(
            FakeSubscriptionRepository(null),
            FakeTagRepository(10L, 11L),
            AddTransactionUseCase(FakeTransactionRepository(), FakeReportRepository()),
        )

        assertEquals(PostSubscriptionChargeResult.NotFound, useCase(99L))
    }

    private class FakeSubscriptionRepository(initial: Subscription?) : SubscriptionRepository {
        var stored: Subscription? = initial
        var lastUpdated: Subscription? = null

        override fun observeAll(): Flow<List<Subscription>> = flowOf(listOfNotNull(stored))

        override suspend fun getById(id: Long): Subscription? = stored?.takeIf { it.id == id }

        override suspend fun add(subscription: Subscription): Long = 0L

        override suspend fun update(subscription: Subscription) {
            lastUpdated = subscription
            stored = subscription
        }

        override suspend fun delete(id: Long) = Unit
    }

    private class FakeTagRepository(
        private val studyRootId: Long,
        private val subscriptionTagId: Long,
    ) : TagRepository {
        override fun observeChildren(parentId: Long?) = flowOf(emptyList<Tag>())

        override suspend fun getChildren(parentId: Long?): List<Tag> = when (parentId) {
            null -> listOf(Tag(id = studyRootId, name = "学习", parentId = null, sortOrder = 0))
            studyRootId -> listOf(
                Tag(id = subscriptionTagId, name = "订阅", parentId = studyRootId, sortOrder = 0),
            )
            else -> emptyList()
        }

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int) = emptyList<Tag>()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?) = 0L

        override suspend fun addRootTag(name: String, icon: String?) = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = error("unused")

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }

    private class FakeTransactionRepository : TransactionRepository {
        val added = mutableListOf<Transaction>()

        override suspend fun add(transaction: Transaction): Long {
            added += transaction
            return added.size.toLong()
        }

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotals = error("unused")

        override suspend fun getExpenseByCategory(startInclusiveMs: Long, endExclusiveMs: Long): List<CategoryExpense> = error("unused")

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = error("unused")

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = error("unused")

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = error("unused")

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) = Unit

        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long): Int = 0

        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long): Long? = null
    }

    private class FakeReportRepository : ReportRepository {
        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())
        override suspend fun getByKey(type: ReportPeriodType, periodKey: String): Report? = null
        override suspend fun upsert(report: Report) = Unit
        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }
}
