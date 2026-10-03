package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.PostSubscriptionChargeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 订阅开支相关 UseCase 接线。 */
@Module
@InstallIn(SingletonComponent::class)
object SubscriptionModule {

    @Provides
    fun providePostSubscriptionChargeUseCase(
        subscriptionRepository: SubscriptionRepository,
        tagRepository: TagRepository,
        addTransactionUseCase: AddTransactionUseCase,
    ): PostSubscriptionChargeUseCase = PostSubscriptionChargeUseCase(
        subscriptionRepository = subscriptionRepository,
        tagRepository = tagRepository,
        addTransactionUseCase = addTransactionUseCase,
    )
}
