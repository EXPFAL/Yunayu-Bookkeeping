package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.MomComparison
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepReadReportUseCaseTest {

    @Test
    fun `unavailable parser returns null without upsert`() = runTest {
        val repo = FakeReportRepository()
        val useCase = DeepReadReportUseCase(FakeParser(available = false), repo)
        assertNull(useCase(sampleReport(), emptySeries()))
        assertTrue(repo.upserted.isEmpty())
    }

    @Test
    fun `success writes analysis text`() = runTest {
        val report = sampleReport()
        val repo = FakeReportRepository().apply { seed(report) }
        val useCase = DeepReadReportUseCase(
            FakeParser(available = true, reply = "这是一段深读复盘。"),
            repo,
        )
        val text = useCase(report, emptySeries())
        assertEquals("这是一段深读复盘。", text)
        assertEquals("这是一段深读复盘。", repo.upserted.single().analysisText)
    }

    @Test
    fun `blank model output returns null`() = runTest {
        val report = sampleReport()
        val repo = FakeReportRepository().apply { seed(report) }
        val useCase = DeepReadReportUseCase(FakeParser(available = true, reply = "  "), repo)
        assertNull(useCase(report, emptySeries()))
        assertTrue(repo.upserted.isEmpty())
    }

    private fun emptySeries() = ReportSeriesSnapshot(
        points = emptyList(),
        mom = MomComparison(0, 0, 0, null),
        topIncreases = emptyList(),
        topDecreases = emptyList(),
    )

    private fun sampleReport() = Report(
        id = 1L,
        periodType = ReportPeriodType.MONTHLY,
        periodKey = "2026-07",
        windowStartMs = 100L,
        windowEndMs = 200L,
        incomeCents = 5_000L,
        expenseCents = 3_000L,
        topCategories = emptyList(),
        prevIncomeCents = 4_000L,
        prevExpenseCents = 2_500L,
        localInsights = listOf(
            LocalInsight(LocalInsightKind.STORY, "本期故事", "本地叙事摘要"),
        ),
        analysisText = null,
        status = ReportStatus.SUCCESS,
        generatedAtMs = 1L,
    )

    private class FakeParser(
        private val available: Boolean,
        private val reply: String? = null,
    ) : NLTransactionParser {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun generate(systemInstruction: String, userText: String): String? = reply
    }

    private class FakeReportRepository : ReportRepository {
        val upserted = mutableListOf<Report>()
        private val byKey = mutableMapOf<Pair<ReportPeriodType, String>, Report>()

        fun seed(report: Report) {
            byKey[report.periodType to report.periodKey] = report
        }

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())
        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? =
            byKey[periodType to periodKey]
        override suspend fun upsert(report: Report) {
            upserted += report
            byKey[report.periodType to report.periodKey] = report
        }
        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }
}
