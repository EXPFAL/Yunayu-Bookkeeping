package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.repository.TransferRepository
import com.expfal.yunayu.domain.usecase.DeleteTransactionUseCase
import com.expfal.yunayu.domain.usecase.DeleteTransferUseCase
import com.expfal.yunayu.domain.usecase.UpdateTransactionUseCase
import com.expfal.yunayu.domain.usecase.UpdateTransferUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 「收支管理」UseCase 接线模块。
 *
 * [DeleteTransactionUseCase] / [DeleteTransferUseCase] / [UpdateTransactionUseCase] /
 * [UpdateTransferUseCase] 采用构造注入、类本身不带 [dagger.inject.Inject]，故在此通过
 * [Provides] 显式组装。
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
    fun provideDeleteTransferUseCase(
        transferRepository: TransferRepository,
    ): DeleteTransferUseCase = DeleteTransferUseCase(transferRepository)

    @Provides
    fun provideUpdateTransactionUseCase(
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
    ): UpdateTransactionUseCase = UpdateTransactionUseCase(transactionRepository, reportRepository)

    @Provides
    fun provideUpdateTransferUseCase(
        transferRepository: TransferRepository,
    ): UpdateTransferUseCase = UpdateTransferUseCase(transferRepository)
}
