package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 月度预算引擎接口。
 *
 * 核心算法：周额度 = 剩余总额 ÷ 剩余天数 × 7；额度不落库，由
 * [kotlinx.coroutines.flow.combine]（月度预算 + 当月支出聚合）实时推导，
 * 保证数据单一事实来源。引擎只产出数据不产出文案。
 */
interface MonthlyBudgetEngine {

    /** 观察 `today` 视角下的月度预算快照。 */
    fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot>
}
