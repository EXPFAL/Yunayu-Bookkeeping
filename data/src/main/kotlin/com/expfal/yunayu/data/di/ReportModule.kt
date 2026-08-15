package com.expfal.yunayu.data.di

import com.expfal.yunayu.data.report.ApiReportAnalyzer
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.ReportAnalyzer
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 报告生成链路装配模块：将 OpenAI 兼容在线实现 [ApiReportAnalyzer] 暴露为 [ReportAnalyzer] 单例，
 * 并装配 [GenerateReportUseCase] / [EnsureReportsUseCase]（单例，确保 Mutex 防重入全局生效）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {

    @Provides
    @Singleton
    fun provideReportAnalyzer(nlApiConfigRepository: NlApiConfigRepository): ReportAnalyzer =
        ApiReportAnalyzer(nlApiConfigRepository)

    @Provides
    @Singleton
    fun provideGenerateReportUseCase(
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
        analyzer: ReportAnalyzer,
    ): GenerateReportUseCase = GenerateReportUseCase(transactionRepository, reportRepository, analyzer)

    @Provides
    @Singleton
    fun provideEnsureReportsUseCase(
        reportRepository: ReportRepository,
        generateReportUseCase: GenerateReportUseCase,
    ): EnsureReportsUseCase = EnsureReportsUseCase(reportRepository, generateReportUseCase)
}
