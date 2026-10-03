package com.expfal.yunayu.domain.model

/**
 * 账本备份快照（整库替换用）。不含 API Key。
 *
 * [format] / [formatVersion] / [dbVersion] 用于导入前校验；[dbVersion] 须与当前 Room schema 一致。
 */
data class LedgerBackup(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val dbVersion: Int,
    val exportedAt: Long,
    val accounts: List<BackupAccount>,
    val tags: List<BackupTag>,
    val transactions: List<BackupTransaction>,
    val transfers: List<BackupTransfer>,
    val subscriptions: List<BackupSubscription>,
    val reports: List<BackupReport>,
    val monthlyBudgetCents: Long,
    val lastUsedAccountId: Long?,
    val subscriptionReminderKeys: Set<String>,
) {
    companion object {
        const val FORMAT = "yunayu-backup"
        const val FORMAT_VERSION = 1

        /** 与 [com.expfal.yunayu.data.local.YunayuDatabase] version 对齐。 */
        const val CURRENT_DB_VERSION = 10
    }
}

data class BackupAccount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val initialBalanceCents: Long,
)

data class BackupTag(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupTransaction(
    val id: Long,
    val amountCents: Long,
    val type: String,
    val note: String?,
    val tagId: Long?,
    val accountId: Long?,
    val occurredAt: Long,
    val createdAt: Long,
)

data class BackupTransfer(
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
)

data class BackupSubscription(
    val id: Long,
    val name: String,
    val amountCents: Long,
    val billingCycle: String,
    val note: String?,
    val isActive: Boolean,
    val billingStartAt: Long,
    val lastPostedDueAt: Long?,
    val lastPostedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupReport(
    val id: Long,
    val reportType: String,
    val periodKey: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val incomeCents: Long,
    val expenseCents: Long,
    val topCategories: String,
    val prevIncomeCents: Long,
    val prevExpenseCents: Long,
    val analysisText: String?,
    val status: String,
    val engine: String,
    val contentVersion: String,
    val generatedAt: Long,
    val localInsights: String,
)
