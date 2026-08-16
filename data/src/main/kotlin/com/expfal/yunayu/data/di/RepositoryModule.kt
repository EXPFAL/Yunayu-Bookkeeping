package com.expfal.yunayu.data.di

import com.expfal.yunayu.data.repository.AccountRepositoryImpl
import com.expfal.yunayu.data.repository.MonthlyBudgetRepositoryImpl
import com.expfal.yunayu.data.repository.NlApiConfigRepositoryImpl
import com.expfal.yunayu.data.repository.ReportRepositoryImpl
import com.expfal.yunayu.data.repository.RoomTagMergeExecutor
import com.expfal.yunayu.data.repository.TagMergeExecutor
import com.expfal.yunayu.data.repository.TagRepositoryImpl
import com.expfal.yunayu.data.repository.TransactionRepositoryImpl
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 仓储接口绑定模块：:ui 面向 :domain 接口编程，经 Hilt 注入具体实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindMonthlyBudgetRepository(impl: MonthlyBudgetRepositoryImpl): MonthlyBudgetRepository

    @Binds
    @Singleton
    abstract fun bindNlApiConfigRepository(impl: NlApiConfigRepositoryImpl): NlApiConfigRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    @Singleton
    abstract fun bindTagMergeExecutor(impl: RoomTagMergeExecutor): TagMergeExecutor
}
