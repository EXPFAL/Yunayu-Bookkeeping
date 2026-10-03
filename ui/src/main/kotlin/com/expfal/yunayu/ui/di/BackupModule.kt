package com.expfal.yunayu.ui.di

import com.expfal.yunayu.domain.repository.BackupRepository
import com.expfal.yunayu.domain.usecase.ExportBackupUseCase
import com.expfal.yunayu.domain.usecase.ImportBackupUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 账本备份 UseCase 接线。 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    fun provideExportBackupUseCase(
        backupRepository: BackupRepository,
    ): ExportBackupUseCase = ExportBackupUseCase(backupRepository)

    @Provides
    fun provideImportBackupUseCase(
        backupRepository: BackupRepository,
    ): ImportBackupUseCase = ImportBackupUseCase(backupRepository)
}
