package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.util.TimeWindows
import java.time.LocalDate

/** 待推送的上周周报通知。 */
data class WeeklyReportNotification(
    val periodKey: String,
    val expenseCents: Long,
)

/**
 * 补生成上周周报（若缺失），并在首次 SUCCESS 时返回可推送的通知载荷。
 */
class CheckWeeklyReportNotificationUseCase(
    private val ensureReportsUseCase: EnsureReportsUseCase,
    private val reportRepository: ReportRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now()): WeeklyReportNotification? {
        ensureReportsUseCase.ensure(today)
        val window = TimeWindows.previousWeekWindow(today)
        if (notificationPreferencesRepository.wasWeeklyReportNotified(window.periodKey)) return null
        val report = reportRepository.getByKey(ReportPeriodType.WEEKLY, window.periodKey) ?: return null
        if (report.status != ReportStatus.SUCCESS) return null
        return WeeklyReportNotification(
            periodKey = window.periodKey,
            expenseCents = report.expenseCents,
        )
    }
}
