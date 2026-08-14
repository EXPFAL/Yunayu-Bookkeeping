package com.expfal.yunayu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 月度预算 DataStore 实例（单例，按名称去重）。 */
private val Context.budgetDataStore by preferencesDataStore(name = "budget_prefs")

/** 月度预算额度的 DataStore 键。 */
internal val MONTHLY_BUDGET_CENTS_KEY = longPreferencesKey("monthly_budget_cents")

/** [MonthlyBudgetRepository] 的 DataStore 实现，额度以「分」持久化。 */
@Singleton
class MonthlyBudgetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MonthlyBudgetRepository {

    override fun observeMonthlyBudgetCents(): Flow<Long> =
        context.budgetDataStore.data.map { preferences -> preferences[MONTHLY_BUDGET_CENTS_KEY] ?: 0L }

    override suspend fun saveMonthlyBudgetCents(cents: Long) {
        context.budgetDataStore.edit { preferences -> preferences[MONTHLY_BUDGET_CENTS_KEY] = cents }
    }
}
