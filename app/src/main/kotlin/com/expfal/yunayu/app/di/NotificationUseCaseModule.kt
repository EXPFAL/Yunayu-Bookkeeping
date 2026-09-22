package com.expfal.yunayu.app.di

import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.usecase.CheckSubscriptionRemindersUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NotificationUseCaseModule {

    @Provides
    fun provideCheckSubscriptionRemindersUseCase(
        subscriptionRepository: SubscriptionRepository,
        notificationPreferencesRepository: NotificationPreferencesRepository,
    ): CheckSubscriptionRemindersUseCase = CheckSubscriptionRemindersUseCase(
        subscriptionRepository,
        notificationPreferencesRepository,
    )
}
