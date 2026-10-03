package com.expfal.yunayu.data.repository

import androidx.room.withTransaction
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.ReportDao
import com.expfal.yunayu.data.local.dao.SubscriptionDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.dao.TransferDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.data.local.entity.ReportEntity
import com.expfal.yunayu.data.local.entity.SubscriptionEntity
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.data.local.entity.TransferEntity
import com.expfal.yunayu.domain.model.BackupAccount
import com.expfal.yunayu.domain.model.BackupReport
import com.expfal.yunayu.domain.model.BackupSubscription
import com.expfal.yunayu.domain.model.BackupTag
import com.expfal.yunayu.domain.model.BackupTransaction
import com.expfal.yunayu.domain.model.BackupTransfer
import com.expfal.yunayu.domain.model.LedgerBackup
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.BackupRepository
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** [BackupRepository]：Room 全表快照 + DataStore 偏好（不含 API Key）。 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: YunayuDatabase,
    private val accountDao: AccountDao,
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
    private val transferDao: TransferDao,
    private val subscriptionDao: SubscriptionDao,
    private val reportDao: ReportDao,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val accountRepository: AccountRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
) : BackupRepository {

    override suspend fun loadSnapshot(): LedgerBackup {
        val accounts = accountDao.getAll().map { it.toBackup() }
        val tags = tagDao.getAll().map { it.toBackup() }
        val transactions = transactionDao.getAll().map { it.toBackup() }
        val transfers = transferDao.getAll().map { it.toBackup() }
        val subscriptions = subscriptionDao.getAll().map { it.toBackup() }
        val reports = reportDao.getAll().map { it.toBackup() }
        return LedgerBackup(
            dbVersion = LedgerBackup.CURRENT_DB_VERSION,
            exportedAt = System.currentTimeMillis(),
            accounts = accounts,
            tags = tags,
            transactions = transactions,
            transfers = transfers,
            subscriptions = subscriptions,
            reports = reports,
            monthlyBudgetCents = monthlyBudgetRepository.observeMonthlyBudgetCents().first(),
            lastUsedAccountId = accountRepository.observeLastUsedAccountId().first(),
            subscriptionReminderKeys = notificationPreferencesRepository.getSubscriptionReminderKeys(),
        )
    }

    override suspend fun replaceWithSnapshot(backup: LedgerBackup) {
        database.withTransaction {
            // 先清子表再清父表，避免 FK 失败
            transactionDao.deleteAll()
            transferDao.deleteAll()
            reportDao.deleteAll()
            subscriptionDao.deleteAll()
            tagDao.deleteAll()
            accountDao.deleteAll()

            if (backup.accounts.isNotEmpty()) {
                accountDao.insertAll(backup.accounts.map { it.toEntity() })
            }
            // 根标签先于子标签
            val roots = backup.tags.filter { it.parentId == null }
            val children = backup.tags.filter { it.parentId != null }
            if (roots.isNotEmpty()) {
                tagDao.insertAll(roots.map { it.toEntity() })
            }
            if (children.isNotEmpty()) {
                tagDao.insertAll(children.map { it.toEntity() })
            }
            if (backup.transactions.isNotEmpty()) {
                transactionDao.insertAll(backup.transactions.map { it.toEntity() })
            }
            if (backup.transfers.isNotEmpty()) {
                transferDao.insertAll(backup.transfers.map { it.toEntity() })
            }
            if (backup.subscriptions.isNotEmpty()) {
                subscriptionDao.insertAll(backup.subscriptions.map { it.toEntity() })
            }
            if (backup.reports.isNotEmpty()) {
                reportDao.insertAll(backup.reports.map { it.toEntity() })
            }
        }
        monthlyBudgetRepository.saveMonthlyBudgetCents(backup.monthlyBudgetCents)
        accountRepository.saveLastUsedAccountId(backup.lastUsedAccountId)
        notificationPreferencesRepository.replaceSubscriptionReminderKeys(backup.subscriptionReminderKeys)
    }

    private fun AccountEntity.toBackup() = BackupAccount(
        id = id,
        name = name,
        createdAt = createdAt,
        initialBalanceCents = initialBalanceCents,
    )

    private fun BackupAccount.toEntity() = AccountEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        initialBalanceCents = initialBalanceCents,
    )

    private fun TagEntity.toBackup() = BackupTag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = sortOrder,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BackupTag.toEntity() = TagEntity(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = sortOrder,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun TransactionEntity.toBackup() = BackupTransaction(
        id = id,
        amountCents = amountCents,
        type = type,
        note = note,
        tagId = tagId,
        accountId = accountId,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun BackupTransaction.toEntity() = TransactionEntity(
        id = id,
        amountCents = amountCents,
        type = type,
        note = note,
        tagId = tagId,
        accountId = accountId,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun TransferEntity.toBackup() = BackupTransfer(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountCents = amountCents,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun BackupTransfer.toEntity() = TransferEntity(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountCents = amountCents,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun SubscriptionEntity.toBackup() = BackupSubscription(
        id = id,
        name = name,
        amountCents = amountCents,
        billingCycle = billingCycle,
        note = note,
        isActive = isActive,
        billingStartAt = billingStartAt,
        lastPostedDueAt = lastPostedDueAt,
        lastPostedAt = lastPostedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BackupSubscription.toEntity() = SubscriptionEntity(
        id = id,
        name = name,
        amountCents = amountCents,
        billingCycle = billingCycle,
        note = note,
        isActive = isActive,
        billingStartAt = billingStartAt,
        lastPostedDueAt = lastPostedDueAt,
        lastPostedAt = lastPostedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ReportEntity.toBackup() = BackupReport(
        id = id,
        reportType = reportType,
        periodKey = periodKey,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        topCategories = topCategories,
        prevIncomeCents = prevIncomeCents,
        prevExpenseCents = prevExpenseCents,
        analysisText = analysisText,
        status = status,
        engine = engine,
        contentVersion = contentVersion,
        generatedAt = generatedAt,
        localInsights = localInsights,
    )

    private fun BackupReport.toEntity() = ReportEntity(
        id = id,
        reportType = reportType,
        periodKey = periodKey,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        topCategories = topCategories,
        prevIncomeCents = prevIncomeCents,
        prevExpenseCents = prevExpenseCents,
        analysisText = analysisText,
        status = status,
        engine = engine,
        contentVersion = contentVersion,
        generatedAt = generatedAt,
        localInsights = localInsights,
    )
}
