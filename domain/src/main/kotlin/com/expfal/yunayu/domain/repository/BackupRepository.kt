package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.LedgerBackup

/** 账本备份仓储：加载快照与整库替换（不含 API Key）。 */
interface BackupRepository {

    /** 从本机 Room + DataStore 组装快照。 */
    suspend fun loadSnapshot(): LedgerBackup

    /**
     * 用 [backup] 整库替换本机账本与相关偏好。
     * 调用方须已校验 format / dbVersion；实现侧在单事务内清空再写入。
     */
    suspend fun replaceWithSnapshot(backup: LedgerBackup)
}
