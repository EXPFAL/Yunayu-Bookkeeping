package com.expfal.yunayu.data.di

import com.expfal.yunayu.data.BuildConfig
import com.expfal.yunayu.data.nlparse.ApiNlParser
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.ParseNaturalLanguageTransactionUseCase
import com.expfal.yunayu.domain.repository.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 自然语言解析引擎装配模块：将 OpenAI 兼容在线实现 [ApiNlParser] 暴露为 Hilt 可注入的
 * [NLTransactionParser] 单例，连接参数来自 [BuildConfig]（构建期注入，密钥不入库）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NlParseModule {

    @Provides
    @Singleton
    fun provideNlTransactionParser(): NLTransactionParser =
        ApiNlParser(
            baseUrl = BuildConfig.NL_API_BASE_URL,
            model = BuildConfig.NL_API_MODEL,
            apiKey = BuildConfig.NL_API_KEY,
        )

    @Provides
    fun provideParseNaturalLanguageTransactionUseCase(
        parser: NLTransactionParser,
        tagRepository: TagRepository,
    ): ParseNaturalLanguageTransactionUseCase =
        ParseNaturalLanguageTransactionUseCase(parser, tagRepository)
}
