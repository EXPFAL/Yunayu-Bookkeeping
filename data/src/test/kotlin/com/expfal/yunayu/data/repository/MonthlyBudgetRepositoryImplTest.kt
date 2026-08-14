package com.expfal.yunayu.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 月度预算 DataStore 持久化语义的 JVM 直测。
 *
 * [MonthlyBudgetRepositoryImpl] 的 DataStore 实例经 `preferencesDataStore` 绑定到应用 Context，
 * JVM 单测无法直接注入，故此处用 [PreferenceDataStoreFactory.create] 以临时文件直测相同键
 * 与读写语义：初值 0、写后可读、覆盖写。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyBudgetRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `emits zero when budget not set`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempDir, "default.preferences_pb") },
        )

        val value = dataStore.data.map { it[MONTHLY_BUDGET_CENTS_KEY] ?: 0L }.first()

        assertEquals(0L, value)
    }

    @Test
    fun `reads value after write`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempDir, "write.preferences_pb") },
        )

        dataStore.edit { it[MONTHLY_BUDGET_CENTS_KEY] = 123_456L }

        assertEquals(123_456L, dataStore.data.map { it[MONTHLY_BUDGET_CENTS_KEY] ?: 0L }.first())
    }

    @Test
    fun `overwrites existing value`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempDir, "overwrite.preferences_pb") },
        )

        dataStore.edit { it[MONTHLY_BUDGET_CENTS_KEY] = 100L }
        dataStore.edit { it[MONTHLY_BUDGET_CENTS_KEY] = 200L }

        assertEquals(200L, dataStore.data.map { it[MONTHLY_BUDGET_CENTS_KEY] ?: 0L }.first())
    }

    private companion object {
        val MONTHLY_BUDGET_CENTS_KEY = longPreferencesKey("monthly_budget_cents")
    }
}
