package com.expfal.yunayu.data.di

import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.usecase.EnsureAccountsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 账户体系装配模块：装配 [EnsureAccountsUseCase]（单例），供启动时补齐预置账户。
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun provideEnsureAccountsUseCase(
        accountRepository: AccountRepository,
    ): EnsureAccountsUseCase = EnsureAccountsUseCase(accountRepository)
}
