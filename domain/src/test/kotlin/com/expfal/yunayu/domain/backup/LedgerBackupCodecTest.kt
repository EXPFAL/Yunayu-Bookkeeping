package com.expfal.yunayu.domain.backup

import com.expfal.yunayu.domain.model.BackupAccount
import com.expfal.yunayu.domain.model.BackupTag
import com.expfal.yunayu.domain.model.BackupTransaction
import com.expfal.yunayu.domain.model.LedgerBackup
import com.expfal.yunayu.domain.repository.BackupRepository
import com.expfal.yunayu.domain.usecase.ExportBackupUseCase
import com.expfal.yunayu.domain.usecase.ImportBackupUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerBackupCodecTest {

    @Test
    fun `encode decode roundtrip preserves ledger fields`() {
        val original = sampleBackup()
        val json = LedgerBackupCodec.encode(original)
        assertTrue(!json.contains("apiKey", ignoreCase = true))
        val decoded = LedgerBackupCodec.decode(json)
        assertEquals(original.format, decoded.format)
        assertEquals(original.formatVersion, decoded.formatVersion)
        assertEquals(original.dbVersion, decoded.dbVersion)
        assertEquals(original.accounts, decoded.accounts)
        assertEquals(original.tags, decoded.tags)
        assertEquals(original.transactions, decoded.transactions)
        assertEquals(original.monthlyBudgetCents, decoded.monthlyBudgetCents)
        assertEquals(original.lastUsedAccountId, decoded.lastUsedAccountId)
        assertEquals(original.subscriptionReminderKeys, decoded.subscriptionReminderKeys)
    }

    @Test
    fun `decode rejects missing format`() {
        val error = runCatching {
            LedgerBackupCodec.decode("""{"formatVersion":1,"dbVersion":10,"exportedAt":1}""")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `import rejects wrong format`() = runTest {
        val repo = FakeBackupRepository()
        val useCase = ImportBackupUseCase(repo)
        val bad = sampleBackup().copy(format = "other")
        val error = runCatching { useCase(LedgerBackupCodec.encode(bad)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, repo.replaceCount)
    }

    @Test
    fun `import rejects wrong dbVersion`() = runTest {
        val repo = FakeBackupRepository()
        val useCase = ImportBackupUseCase(repo)
        val bad = sampleBackup().copy(dbVersion = 9)
        val error = runCatching { useCase(LedgerBackupCodec.encode(bad)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, repo.replaceCount)
    }

    @Test
    fun `export use case returns codec json`() = runTest {
        val snap = sampleBackup()
        val repo = FakeBackupRepository(snap)
        val json = ExportBackupUseCase(repo)()
        assertEquals(snap.accounts, LedgerBackupCodec.decode(json).accounts)
    }

    @Test
    fun `import use case replaces on valid json`() = runTest {
        val repo = FakeBackupRepository()
        ImportBackupUseCase(repo)(LedgerBackupCodec.encode(sampleBackup()))
        assertEquals(1, repo.replaceCount)
    }

    private fun sampleBackup() = LedgerBackup(
        dbVersion = LedgerBackup.CURRENT_DB_VERSION,
        exportedAt = 1_700_000_000_000L,
        accounts = listOf(
            BackupAccount(1L, "微信", 1L, 100L),
        ),
        tags = listOf(
            BackupTag(10L, "学习", null, 0, null, 1L, 1L),
            BackupTag(11L, "教材", 10L, 0, null, 1L, 1L),
        ),
        transactions = listOf(
            BackupTransaction(100L, 500L, "EXPENSE", "书", 11L, 1L, 2L, 3L),
        ),
        transfers = emptyList(),
        subscriptions = emptyList(),
        reports = emptyList(),
        monthlyBudgetCents = 200_00L,
        lastUsedAccountId = 1L,
        subscriptionReminderKeys = setOf("k1"),
    )

    private class FakeBackupRepository(
        private val snapshot: LedgerBackup = LedgerBackup(
            dbVersion = LedgerBackup.CURRENT_DB_VERSION,
            exportedAt = 0L,
            accounts = emptyList(),
            tags = emptyList(),
            transactions = emptyList(),
            transfers = emptyList(),
            subscriptions = emptyList(),
            reports = emptyList(),
            monthlyBudgetCents = 0L,
            lastUsedAccountId = null,
            subscriptionReminderKeys = emptySet(),
        ),
    ) : BackupRepository {
        var replaceCount = 0
            private set

        override suspend fun loadSnapshot(): LedgerBackup = snapshot

        override suspend fun replaceWithSnapshot(backup: LedgerBackup) {
            replaceCount++
        }
    }
}
