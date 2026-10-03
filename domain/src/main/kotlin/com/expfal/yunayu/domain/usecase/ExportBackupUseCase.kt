package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.backup.LedgerBackupCodec
import com.expfal.yunayu.domain.repository.BackupRepository

/** 导出本机账本为 JSON 文本（不含 API Key）。 */
class ExportBackupUseCase(
    private val backupRepository: BackupRepository,
) {

    suspend operator fun invoke(): String {
        val snapshot = backupRepository.loadSnapshot()
        return LedgerBackupCodec.encode(snapshot)
    }
}
