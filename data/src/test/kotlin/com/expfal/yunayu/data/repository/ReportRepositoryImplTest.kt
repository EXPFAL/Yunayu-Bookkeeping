package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.ReportDao
import com.expfal.yunayu.data.local.entity.ReportEntity
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [ReportRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class ReportRepositoryImplTest {

    @Test
    fun `upsert maps report fields to entity with engine and version`() = runTest {
        val dao = FakeReportDao()
        val repository = ReportRepositoryImpl(dao)

        repository.upsert(
            Report(
                id = 9L,
                periodType = ReportPeriodType.MONTHLY,
                periodKey = "2026-07",
                windowStartMs = 100L,
                windowEndMs = 200L,
                incomeCents = 5_000L,
                expenseCents = 3_000L,
                topCategories = listOf(
                    CategoryShare("餐饮", 1_500L, 50),
                    CategoryShare(null, 500L, 16),
                ),
                prevIncomeCents = 4_000L,
                prevExpenseCents = 2_500L,
                analysisText = "分析文本",
                status = ReportStatus.SUCCESS,
                generatedAtMs = 999L,
            ),
        )

        val entity = dao.upserted.single()
        assertEquals(9L, entity.id)
        assertEquals("MONTHLY", entity.reportType)
        assertEquals("2026-07", entity.periodKey)
        assertEquals(100L, entity.windowStartMs)
        assertEquals(200L, entity.windowEndMs)
        assertEquals(5_000L, entity.incomeCents)
        assertEquals(3_000L, entity.expenseCents)
        assertEquals("餐饮:1500:50;:500:16", entity.topCategories)
        assertEquals(4_000L, entity.prevIncomeCents)
        assertEquals(2_500L, entity.prevExpenseCents)
        assertEquals("分析文本", entity.analysisText)
        assertEquals("SUCCESS", entity.status)
        assertEquals("api", entity.engine)
        assertEquals("1", entity.contentVersion)
        assertEquals(999L, entity.generatedAt)
    }

    @Test
    fun `observeByType maps entities to domain reports`() = runTest {
        val dao = FakeReportDao().apply {
            observeResult = flowOf(
                listOf(
                    ReportEntity(
                        id = 1L,
                        reportType = "ANNUAL",
                        periodKey = "2025",
                        windowStartMs = 10L,
                        windowEndMs = 20L,
                        incomeCents = 1_000L,
                        expenseCents = 800L,
                        topCategories = "餐饮:400:50",
                        prevIncomeCents = 900L,
                        prevExpenseCents = 700L,
                        analysisText = null,
                        status = "FAILED",
                        engine = "api",
                        contentVersion = "1",
                        generatedAt = 5L,
                    ),
                ),
            )
        }
        val repository = ReportRepositoryImpl(dao)

        val reports = repository.observeByType(ReportPeriodType.ANNUAL).first()

        assertEquals(1, reports.size)
        val report = reports.single()
        assertEquals(ReportPeriodType.ANNUAL, report.periodType)
        assertEquals("2025", report.periodKey)
        assertEquals(ReportStatus.FAILED, report.status)
        assertNull(report.analysisText)
        assertEquals(listOf(CategoryShare("餐饮", 400L, 50)), report.topCategories)
        assertEquals(1_000L, report.incomeCents)
    }

    @Test
    fun `observeByType falls back on unknown enum values`() = runTest {
        val dao = FakeReportDao().apply {
            observeResult = flowOf(
                listOf(
                    ReportEntity(
                        id = 1L,
                        reportType = "QUARTERLY",
                        periodKey = "2026-Q1",
                        windowStartMs = 0L,
                        windowEndMs = 0L,
                        incomeCents = 0L,
                        expenseCents = 0L,
                        topCategories = "",
                        prevIncomeCents = 0L,
                        prevExpenseCents = 0L,
                        analysisText = null,
                        status = "PENDING",
                        engine = "api",
                        contentVersion = "1",
                        generatedAt = 0L,
                    ),
                ),
            )
        }
        val repository = ReportRepositoryImpl(dao)

        val report = repository.observeByType(ReportPeriodType.MONTHLY).first().single()

        assertEquals(ReportPeriodType.MONTHLY, report.periodType)
        assertEquals(ReportStatus.FAILED, report.status)
        assertEquals(emptyList<CategoryShare>(), report.topCategories)
    }

    @Test
    fun `getByKey returns null when dao returns null`() = runTest {
        val repository = ReportRepositoryImpl(FakeReportDao())

        val report = repository.getByKey(ReportPeriodType.MONTHLY, "2026-07")

        assertNull(report)
    }

    @Test
    fun `serialize and parse top categories round trip with null name`() {
        val categories = listOf(
            CategoryShare("餐饮", 1_500L, 50),
            CategoryShare(null, 500L, 16),
        )

        val serialized = ReportRepositoryImpl.serializeTopCategories(categories)
        val parsed = ReportRepositoryImpl.parseTopCategories(serialized)

        assertEquals("餐饮:1500:50;:500:16", serialized)
        assertEquals(categories, parsed)
    }

    @Test
    fun `serialize replaces colon and semicolon in tag name for round trip`() {
        val categories = listOf(
            CategoryShare("早:餐;套餐", 1_000L, 33),
            CategoryShare(null, 500L, 16),
        )

        val serialized = ReportRepositoryImpl.serializeTopCategories(categories)
        val parsed = ReportRepositoryImpl.parseTopCategories(serialized)

        assertEquals("早：餐；套餐:1000:33;:500:16", serialized)
        assertEquals(2, parsed.size)
        assertEquals("早：餐；套餐", parsed[0].tagName)
        assertEquals(1_000L, parsed[0].cents)
        assertEquals(33, parsed[0].percent)
        assertNull(parsed[1].tagName)
    }

    @Test
    fun `parse top categories tolerates malformed entries`() {
        val parsed = ReportRepositoryImpl.parseTopCategories("餐饮:1500:50;bad;:500:16;交通::10;:1:2")

        assertEquals(
            listOf(
                CategoryShare("餐饮", 1_500L, 50),
                CategoryShare(null, 500L, 16),
                CategoryShare(null, 1L, 2),
            ),
            parsed,
        )
    }

    @Test
    fun `parse top categories returns empty list for garbage`() {
        assertEquals(emptyList<CategoryShare>(), ReportRepositoryImpl.parseTopCategories(""))
        assertEquals(emptyList<CategoryShare>(), ReportRepositoryImpl.parseTopCategories(";;;"))
        assertEquals(emptyList<CategoryShare>(), ReportRepositoryImpl.parseTopCategories("abc"))
    }

    /** [ReportDao] 手写 fake：记录 upsert 入参，可返回预置观察流。 */
    private class FakeReportDao : ReportDao {
        val upserted = mutableListOf<ReportEntity>()
        var observeResult: Flow<List<ReportEntity>> = flowOf(emptyList())
        var getByKeyResult: ReportEntity? = null
        val invalidateCalls = mutableListOf<Long>()

        override fun observeByType(type: String): Flow<List<ReportEntity>> = observeResult

        override suspend fun getByKey(type: String, periodKey: String): ReportEntity? = getByKeyResult

        override suspend fun upsert(report: ReportEntity) {
            upserted += report
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidateCalls += epochMillis
        }
    }
}
