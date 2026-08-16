package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.FindMergeCandidatesUseCase
import com.expfal.yunayu.domain.usecase.MergeTagsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 「标签整合」UseCase 接线模块。
 *
 * [FindMergeCandidatesUseCase] 与 [MergeTagsUseCase] 采用构造注入、类本身不带
 * [dagger.inject.Inject]，故在此通过 [Provides] 显式组装，供标签管理整合流程与整理页
 * 整合提示共用（[FindMergeCandidatesUseCase] 亦被 OrganizeViewModel 复用，此处为唯一绑定源）。
 */
@Module
@InstallIn(SingletonComponent::class)
object TagMergeModule {

    @Provides
    fun provideFindMergeCandidatesUseCase(
        tagRepository: TagRepository,
        parser: NLTransactionParser,
    ): FindMergeCandidatesUseCase = FindMergeCandidatesUseCase(tagRepository, parser)

    @Provides
    fun provideMergeTagsUseCase(
        tagRepository: TagRepository,
        transactionRepository: TransactionRepository,
        reportRepository: ReportRepository,
    ): MergeTagsUseCase = MergeTagsUseCase(tagRepository, transactionRepository, reportRepository)
}
