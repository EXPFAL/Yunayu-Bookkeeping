package com.expfal.yunayu.data.di

import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.EnsureIncomeTagsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 收入标签体系装配模块：装配 [EnsureIncomeTagsUseCase]（单例），供启动时补齐收入根类与种子子标签。
 */
@Module
@InstallIn(SingletonComponent::class)
object IncomeTagsModule {

    @Provides
    @Singleton
    fun provideEnsureIncomeTagsUseCase(
        tagRepository: TagRepository,
    ): EnsureIncomeTagsUseCase = EnsureIncomeTagsUseCase(tagRepository)
}
