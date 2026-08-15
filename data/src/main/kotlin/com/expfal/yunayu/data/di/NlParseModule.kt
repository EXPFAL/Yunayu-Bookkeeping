package com.expfal.yunayu.data.di

import com.expfal.yunayu.data.nlparse.ApiNlParser
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.ParseNaturalLanguageTransactionUseCase
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import com.expfal.yunayu.domain.repository.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 自然语言解析引擎装配模块：将 OpenAI 兼容在线实现 [ApiNlParser] 暴露为 Hilt 可注入的
 * [NLTransactionParser] 单例；连接参数由 [NlApiConfigRepository] 运行期提供，构建期
 * [com.expfal.yunayu.data.BuildConfig] 仅作默认兜底（密钥运行期可改、不入库）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NlParseModule {

    @Provides
    @Singleton
    fun provideNlTransactionParser(
        nlApiConfigRepository: NlApiConfigRepository,
    ): NLTransactionParser = ApiNlParser(nlApiConfigRepository)

    @Provides
    fun provideParseNaturalLanguageTransactionUseCase(
        parser: NLTransactionParser,
        tagRepository: TagRepository,
    ): ParseNaturalLanguageTransactionUseCase =
        ParseNaturalLanguageTransactionUseCase(parser, tagRepository)
}
