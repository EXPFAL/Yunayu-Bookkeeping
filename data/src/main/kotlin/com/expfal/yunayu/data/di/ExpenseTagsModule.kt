package com.expfal.yunayu.data.di

import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.EnsureExpenseTagsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 支出种子补齐：装配 [EnsureExpenseTagsUseCase]，供启动时给生活根补增量叶子。 */
@Module
@InstallIn(SingletonComponent::class)
object ExpenseTagsModule {

    @Provides
    @Singleton
    fun provideEnsureExpenseTagsUseCase(
        tagRepository: TagRepository,
    ): EnsureExpenseTagsUseCase = EnsureExpenseTagsUseCase(tagRepository)
}
