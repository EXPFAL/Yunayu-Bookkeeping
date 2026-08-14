package com.expfal.yunayu.data.di

import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngineImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 预算引擎装配模块：将纯领域实现 [MonthlyBudgetEngineImpl] 暴露为 Hilt 可注入的单例。
 *
 * 仓储接口绑定已由 [RepositoryModule] 提供，此处仅补充引擎的构造注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object BudgetModule {

    @Provides
    @Singleton
    fun provideMonthlyBudgetEngine(
        monthlyBudgetRepository: MonthlyBudgetRepository,
        transactionRepository: TransactionRepository,
    ): MonthlyBudgetEngine =
        MonthlyBudgetEngineImpl(monthlyBudgetRepository, transactionRepository)
}
