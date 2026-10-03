package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.backup.LedgerBackupCodec
import com.expfal.yunayu.domain.model.LedgerBackup
import com.expfal.yunayu.domain.repository.BackupRepository

/**
 * 从 JSON 整库替换本机账本。
 *
 * 校验 [LedgerBackup.FORMAT] 与 [LedgerBackup.CURRENT_DB_VERSION]；失败抛
 * [IllegalArgumentException]，不写入。
 */
class ImportBackupUseCase(
    private val backupRepository: BackupRepository,
) {

    suspend operator fun invoke(json: String) {
        val backup = LedgerBackupCodec.decode(json)
        require(backup.format == LedgerBackup.FORMAT) {
            "unsupported backup format: ${backup.format}"
        }
        require(backup.formatVersion == LedgerBackup.FORMAT_VERSION) {
            "unsupported backup formatVersion: ${backup.formatVersion}"
        }
        require(backup.dbVersion == LedgerBackup.CURRENT_DB_VERSION) {
            "backup dbVersion ${backup.dbVersion} != current ${LedgerBackup.CURRENT_DB_VERSION}"
        }
        backupRepository.replaceWithSnapshot(backup)
    }
}
