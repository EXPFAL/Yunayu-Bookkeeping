package com.expfal.yunayu.data.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.ReportDao
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
            .openHelperFactory(foreignKeyEnforcingFactory())
            .addCallback(YunayuDatabase.seedCallback())
            .addMigrations(
                YunayuDatabase.MIGRATION_1_2,
                YunayuDatabase.MIGRATION_2_3,
                YunayuDatabase.MIGRATION_3_4,
            )
            .build()

    /**
     * 返回在 [SupportSQLiteOpenHelper.Callback.onConfigure] 中开启外键强制的工厂。
     *
     * SQLite 默认关闭外键强制；[androidx.room.RoomDatabase.Callback] 没有 onConfigure，
     * 故包装 [FrameworkSQLiteOpenHelperFactory] 的回调，在数据库打开前的配置阶段即开启外键，
     * 确保 tags 子树 CASCADE、transactions SET NULL 在运行时真实生效。
     */
    private fun foreignKeyEnforcingFactory(): SupportSQLiteOpenHelper.Factory =
        SupportSQLiteOpenHelper.Factory { configuration ->
            val wrapped = object : SupportSQLiteOpenHelper.Callback(configuration.callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    configuration.callback.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = configuration.callback.onCreate(db)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    configuration.callback.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    configuration.callback.onDowngrade(db, oldVersion, newVersion)

                override fun onOpen(db: SupportSQLiteDatabase) = configuration.callback.onOpen(db)

                override fun onCorruption(db: SupportSQLiteDatabase) =
                    configuration.callback.onCorruption(db)
            }
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration(
                    configuration.context,
                    configuration.name,
                    wrapped,
                    configuration.useNoBackupDirectory,
                    configuration.allowDataLossOnRecovery,
                ),
            )
        }

    @Provides
    @Singleton
    fun provideTagDao(database: YunayuDatabase): TagDao = database.tagDao()

    @Provides
    @Singleton
    fun provideTransactionDao(database: YunayuDatabase): TransactionDao = database.transactionDao()

    @Provides
    @Singleton
    fun provideReportDao(database: YunayuDatabase): ReportDao = database.reportDao()
}
