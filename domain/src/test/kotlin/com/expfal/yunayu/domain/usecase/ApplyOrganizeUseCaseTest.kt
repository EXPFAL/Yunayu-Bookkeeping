package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.ApplyOrganizeUseCase.ConfirmedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ApplyOrganizeUseCase] 的 JVM 单元测试（手写 fake 三仓储 + coroutines-test）。 */
class ApplyOrganizeUseCaseTest {

    @Test
    fun `attach matches root child full name and assigns`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            childrenByParent[1L] = mutableListOf(Tag(id = 2L, name = "教材", parentId = 1L))
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.ATTACH, "学习·教材", null)),
            recordsById = mapOf(10L to recent(occurredAt = 100L)),
        )

        assertEquals(1, result.appliedCount)
        assertTrue(result.failedRecordIds.isEmpty())
        assertEquals(listOf(mapOf(2L to listOf(10L))), tx.assignTagsCalls)
        assertEquals(listOf(100L), reports.invalidated)
        assertTrue(tags.addSubTagCalls.isEmpty())
    }

    @Test
    fun `attach matches unique bare name across roots`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            childrenByParent[1L] = mutableListOf(Tag(id = 2L, name = "教材", parentId = 1L))
        }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.ATTACH, "教材", null)),
            recordsById = mapOf(10L to recent(occurredAt = 100L)),
        )

        assertEquals(1, result.appliedCount)
        assertEquals(listOf(mapOf(2L to listOf(10L))), tx.assignTagsCalls)
    }

    @Test
    fun `attach fails on ambiguous bare name`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            rootTags += Tag(id = 2L, name = "生活")
            childrenByParent[1L] = mutableListOf(Tag(id = 11L, name = "教材", parentId = 1L))
            childrenByParent[2L] = mutableListOf(Tag(id = 12L, name = "教材", parentId = 2L))
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.ATTACH, "教材", null)),
            recordsById = mapOf(10L to recent(occurredAt = 100L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tx.assignTagsCalls.isEmpty())
        assertTrue(reports.invalidated.isEmpty())
    }

    @Test
    fun `create new sub tag and assigns`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply { rootTags += Tag(id = 1L, name = "学习") }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "考证", "学习")),
            recordsById = mapOf(10L to recent(occurredAt = 200L)),
        )

        assertEquals(1, result.appliedCount)
        assertTrue(result.failedRecordIds.isEmpty())
        assertEquals(listOf(Triple(1L, "考证", null)), tags.addSubTagCalls)
        assertEquals(listOf(mapOf(100L to listOf(10L))), tx.assignTagsCalls)
        assertEquals(listOf(200L), reports.invalidated)
    }

    @Test
    fun `create rejects income record under non income root`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            rootTags += Tag(id = 2L, name = "收入")
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "兼职", "学习")),
            recordsById = mapOf(10L to recent(type = TransactionType.INCOME, occurredAt = 200L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tags.addSubTagCalls.isEmpty())
        assertTrue(tx.assignTagsCalls.isEmpty())
        assertTrue(reports.invalidated.isEmpty())
    }

    @Test
    fun `create attaches income record under income root`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply { rootTags += Tag(id = 2L, name = "收入") }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "兼职", "收入")),
            recordsById = mapOf(10L to recent(type = TransactionType.INCOME, occurredAt = 200L)),
        )

        assertEquals(1, result.appliedCount)
        assertEquals(listOf(Triple(2L, "兼职", null)), tags.addSubTagCalls)
        assertEquals(listOf(mapOf(100L to listOf(10L))), tx.assignTagsCalls)
    }

    @Test
    fun `create rejects unknown root name`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply { rootTags += Tag(id = 1L, name = "学习") }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "考证", "不存在的根")),
            recordsById = mapOf(10L to recent(occurredAt = 200L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tags.addSubTagCalls.isEmpty())
    }

    @Test
    fun `create reuses existing tag on duplicate name`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            childrenByParent[1L] = mutableListOf(Tag(id = 5L, name = "考证", parentId = 1L))
            addSubTagError = DuplicateTagNameException("dup")
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "考证", "学习")),
            recordsById = mapOf(10L to recent(occurredAt = 300L)),
        )

        assertEquals(1, result.appliedCount)
        assertTrue(result.failedRecordIds.isEmpty())
        assertEquals(listOf("考证"), result.reusedTagNames)
        assertEquals(1, tags.addSubTagCalls.size)
        assertEquals(listOf(mapOf(5L to listOf(10L))), tx.assignTagsCalls)
        assertEquals(listOf(300L), reports.invalidated)
    }

    @Test
    fun `attach rejects income record on expense system tag`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            rootTags += Tag(id = 2L, name = "收入")
            childrenByParent[1L] = mutableListOf(Tag(id = 11L, name = "教材", parentId = 1L))
        }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.ATTACH, "学习·教材", null)),
            recordsById = mapOf(10L to recent(type = TransactionType.INCOME, occurredAt = 100L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tx.assignTagsCalls.isEmpty())
    }

    @Test
    fun `attach rejects expense record on income system tag`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 2L, name = "收入")
            childrenByParent[2L] = mutableListOf(Tag(id = 21L, name = "兼职", parentId = 2L))
        }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.ATTACH, "收入·兼职", null)),
            recordsById = mapOf(10L to recent(type = TransactionType.EXPENSE, occurredAt = 100L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tx.assignTagsCalls.isEmpty())
    }

    @Test
    fun `create rejects expense record under income root`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply { rootTags += Tag(id = 2L, name = "收入") }
        val useCase = ApplyOrganizeUseCase(tx, tags, FakeReportRepository())

        val result = useCase(
            items = listOf(ConfirmedItem(10L, Action.CREATE, "兼职", "收入")),
            recordsById = mapOf(10L to recent(type = TransactionType.EXPENSE, occurredAt = 200L)),
        )

        assertEquals(0, result.appliedCount)
        assertEquals(listOf(10L), result.failedRecordIds)
        assertTrue(tags.addSubTagCalls.isEmpty())
        assertTrue(tx.assignTagsCalls.isEmpty())
    }

    @Test
    fun `keeps failed records and applies successful ones`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            rootTags += Tag(id = 2L, name = "生活")
            childrenByParent[1L] = mutableListOf(Tag(id = 11L, name = "教材", parentId = 1L))
            childrenByParent[2L] = mutableListOf(Tag(id = 12L, name = "教材", parentId = 2L))
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        val result = useCase(
            items = listOf(
                ConfirmedItem(10L, Action.ATTACH, "学习·教材", null),
                ConfirmedItem(20L, Action.ATTACH, "教材", null),
            ),
            recordsById = mapOf(
                10L to recent(occurredAt = 100L),
                20L to recent(occurredAt = 200L),
            ),
        )

        assertEquals(1, result.appliedCount)
        assertEquals(listOf(20L), result.failedRecordIds)
        assertEquals(listOf(mapOf(11L to listOf(10L))), tx.assignTagsCalls)
        assertEquals(listOf(100L), reports.invalidated)
    }

    @Test
    fun `dedupes occurredAt when invalidating reports`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            childrenByParent[1L] = mutableListOf(Tag(id = 2L, name = "教材", parentId = 1L))
        }
        val reports = FakeReportRepository()
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        useCase(
            items = listOf(
                ConfirmedItem(10L, Action.ATTACH, "学习·教材", null),
                ConfirmedItem(20L, Action.ATTACH, "学习·教材", null),
            ),
            recordsById = mapOf(
                10L to recent(occurredAt = 100L),
                20L to recent(occurredAt = 100L),
            ),
        )

        assertEquals(listOf(100L), reports.invalidated)
    }

    @Test
    fun `rethrows cancellation exception from invalidate`() = runTest {
        val tx = FakeTransactionRepository()
        val tags = FakeTagRepository().apply {
            rootTags += Tag(id = 1L, name = "学习")
            childrenByParent[1L] = mutableListOf(Tag(id = 2L, name = "教材", parentId = 1L))
        }
        val reports = FakeReportRepository().apply { invalidateError = CancellationException("cancelled") }
        val useCase = ApplyOrganizeUseCase(tx, tags, reports)

        var caught: Throwable? = null
        try {
            useCase(
                items = listOf(ConfirmedItem(10L, Action.ATTACH, "学习·教材", null)),
                recordsById = mapOf(10L to recent(occurredAt = 100L)),
            )
        } catch (throwable: Throwable) {
            caught = throwable
        }

        assertTrue(caught is CancellationException)
        assertEquals(1, tx.assignTagsCalls.size)
    }

    private fun recent(
        type: TransactionType = TransactionType.EXPENSE,
        occurredAt: Long = 0L,
    ) = RecentTransaction(
        id = 10L,
        amountCents = 1_000L,
        type = type,
        tagName = null,
        occurredAt = occurredAt,
    )

    /** [TransactionRepository] 手写 fake：记录 assignTags 入参。 */
    private class FakeTransactionRepository : TransactionRepository {

        val assignTagsCalls = mutableListOf<Map<Long, List<Long>>>()

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) {
            assignTagsCalls += assignments
        }

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
    }

    /** [TagRepository] 手写 fake：预置根 / 子标签，记录 addSubTag 入参。 */
    private class FakeTagRepository : TagRepository {

        val rootTags = mutableListOf<Tag>()
        val childrenByParent = mutableMapOf<Long, MutableList<Tag>>()
        val addSubTagCalls = mutableListOf<Triple<Long, String, String?>>()
        var addSubTagError: Throwable? = null
        private var nextTagId = 100L

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) rootTags else childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(
            sinceEpochMillis: Long,
            type: TransactionType,
            limit: Int,
        ): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
            addSubTagCalls += Triple(parentId, name, icon)
            addSubTagError?.let { throw it }
            val id = nextTagId++
            childrenByParent.getOrPut(parentId) { mutableListOf() } +=
                Tag(id = id, name = name, parentId = parentId)
            return id
        }

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }

    /** [ReportRepository] 手写 fake：记录标脏入参，可配置标脏异常。 */
    private class FakeReportRepository : ReportRepository {

        val invalidated = mutableListOf<Long>()
        var invalidateError: Throwable? = null

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidateError?.let { throw it }
            invalidated += epochMillis
        }
    }
}
