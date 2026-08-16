package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.OrganizeSuggestUseCase
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.ApplyOrganizeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 「整理未分类」UseCase 接线模块。
 *
 * :domain 的 UseCase 采用构造注入、类本身不带 [dagger.inject.Inject]，故在此通过 [Provides]
 * 显式组装，供 [com.expfal.yunayu.ui.screen.organize.OrganizeViewModel] 注入。[NLTransactionParser]
 * 的绑定已在 :data 的 NlParseModule 提供，此处仅作为依赖复用。
 */
@Module
@InstallIn(SingletonComponent::class)
object OrganizeModule {

    @Provides
    fun provideOrganizeSuggestUseCase(
        parser: NLTransactionParser,
    ): OrganizeSuggestUseCase = OrganizeSuggestUseCase(parser)

    @Provides
    fun provideApplyOrganizeUseCase(
        transactionRepository: TransactionRepository,
        tagRepository: TagRepository,
        reportRepository: ReportRepository,
    ): ApplyOrganizeUseCase =
        ApplyOrganizeUseCase(transactionRepository, tagRepository, reportRepository)
}
