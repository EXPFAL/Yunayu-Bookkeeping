package com.expfal.yunayu.app.di

import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.usecase.CheckSubscriptionRemindersUseCase
import com.expfal.yunayu.domain.usecase.CheckWeeklyReportNotificationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NotificationUseCaseModule {

    @Provides
    fun provideCheckSubscriptionRemindersUseCase(
        subscriptionRepository: SubscriptionRepository,
        notificationPreferencesRepository: NotificationPreferencesRepository,
    ): CheckSubscriptionRemindersUseCase = CheckSubscriptionRemindersUseCase(
        subscriptionRepository,
        notificationPreferencesRepository,
    )

    @Provides
    fun provideCheckWeeklyReportNotificationUseCase(
        ensureReportsUseCase: EnsureReportsUseCase,
        reportRepository: ReportRepository,
        notificationPreferencesRepository: NotificationPreferencesRepository,
    ): CheckWeeklyReportNotificationUseCase = CheckWeeklyReportNotificationUseCase(
        ensureReportsUseCase,
        reportRepository,
        notificationPreferencesRepository,
    )
}
