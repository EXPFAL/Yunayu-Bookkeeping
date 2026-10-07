package com.expfal.yunayu.data.di

import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.DeepReadReportUseCase
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.LoadReportSeriesUseCase
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 报告生成链路装配：[GenerateReportUseCase] / [EnsureReportsUseCase] 为单例，确保 Mutex 防重入全局生效。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {

    @Provides
    @Singleton
    fun provideGenerateReportUseCase(
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
        monthlyBudgetRepository: MonthlyBudgetRepository,
    ): GenerateReportUseCase = GenerateReportUseCase(
        transactionRepository,
        reportRepository,
        monthlyBudgetRepository,
    )

    @Provides
    @Singleton
    fun provideEnsureReportsUseCase(
        reportRepository: ReportRepository,
        generateReportUseCase: GenerateReportUseCase,
    ): EnsureReportsUseCase = EnsureReportsUseCase(reportRepository, generateReportUseCase)

    @Provides
    @Singleton
    fun provideLoadReportSeriesUseCase(
        transactionRepository: TransactionRepository,
    ): LoadReportSeriesUseCase = LoadReportSeriesUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideDeepReadReportUseCase(
        nlTransactionParser: NLTransactionParser,
        reportRepository: ReportRepository,
    ): DeepReadReportUseCase = DeepReadReportUseCase(nlTransactionParser, reportRepository)
}
