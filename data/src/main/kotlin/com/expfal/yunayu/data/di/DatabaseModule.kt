package com.expfal.yunayu.data.di

import android.content.Context
import androidx.room.Room
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据库与 DAO 提供模块。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideYunayuDatabase(@ApplicationContext context: Context): YunayuDatabase =
        Room.databaseBuilder(context, YunayuDatabase::class.java, YunayuDatabase.NAME)
            .addCallback(YunayuDatabase.seedCallback())
            .build()

    @Provides
    @Singleton
    fun provideTagDao(database: YunayuDatabase): TagDao = database.tagDao()

    @Provides
    @Singleton
    fun provideTransactionDao(database: YunayuDatabase): TransactionDao = database.transactionDao()

    @Provides
    @Singleton
    fun provideSemesterDao(database: YunayuDatabase): SemesterDao = database.semesterDao()
}
