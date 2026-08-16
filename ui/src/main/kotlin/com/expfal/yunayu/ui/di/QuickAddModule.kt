package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.AddParsedTransactionUseCase
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.GetRecentCategoriesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 「3秒极速记账」UseCase 接线模块。
 *
 * :domain 的 UseCase 采用构造注入、类本身不带 [dagger.inject.Inject]，故在此通过
 * [Provides] 显式组装，供 [com.expfal.yunayu.ui.screen.quickadd.QuickAddViewModel] 注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object QuickAddModule {

    @Provides
    fun provideGetRecentCategoriesUseCase(
        tagRepository: TagRepository,
    ): GetRecentCategoriesUseCase = GetRecentCategoriesUseCase(tagRepository)

    @Provides
    fun provideAddTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): AddTransactionUseCase = AddTransactionUseCase(transactionRepository)

    @Provides
    fun provideAddParsedTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): AddParsedTransactionUseCase = AddParsedTransactionUseCase(transactionRepository)
}
