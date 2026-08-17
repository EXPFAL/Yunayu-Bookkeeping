package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.DeleteTransactionUseCase
import com.expfal.yunayu.domain.usecase.UpdateTransactionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 「收支管理」UseCase 接线模块。
 *
 * [DeleteTransactionUseCase] / [UpdateTransactionUseCase] 采用构造注入、类本身不带
 * [dagger.inject.Inject]，故在此通过 [Provides] 显式组装，供
 * [com.expfal.yunayu.ui.screen.transactionmanage.TransactionManageViewModel] 与
 * [com.expfal.yunayu.ui.screen.transactionmanage.EditTransactionViewModel] 注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object TransactionManageModule {

    @Provides
    fun provideDeleteTransactionUseCase(
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
    ): DeleteTransactionUseCase = DeleteTransactionUseCase(transactionRepository, reportRepository)

    @Provides
    fun provideUpdateTransactionUseCase(
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
    ): UpdateTransactionUseCase = UpdateTransactionUseCase(transactionRepository, reportRepository)
}
