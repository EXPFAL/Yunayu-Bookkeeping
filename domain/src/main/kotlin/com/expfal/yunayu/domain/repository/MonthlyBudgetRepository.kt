package com.expfal.yunayu.domain.repository

import kotlinx.coroutines.flow.Flow

/** 月度预算仓储接口，由 :data 模块实现。金额一律以「分」为单位（Long）。 */
interface MonthlyBudgetRepository {

    /** 观察月度预算额度；用户尚未设置时发射 `0`（由 UI 层转译为引导态）。 */
    fun observeMonthlyBudgetCents(): Flow<Long>

    /** 保存月度预算额度。 */
    suspend fun saveMonthlyBudgetCents(cents: Long)
}
