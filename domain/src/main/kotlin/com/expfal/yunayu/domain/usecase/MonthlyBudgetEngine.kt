package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 月度预算引擎接口。
 *
 * 月剩余 = max(月预算 − 当月支出, 0)。
 * 周额度在预算周起点冻结：周一剩余 × 本周落在本月的天数 ÷ 周一到月末天数；
 * 本周还可花 = max(周额度 − 本周∩本月支出, 0)。额度不落库，由
 * [kotlinx.coroutines.flow.combine]（月度预算 + 当月支出 + 本周支出）实时推导。
 * 引擎只产出数据不产出文案。
 */
interface MonthlyBudgetEngine {

    /** 观察 `today` 视角下的月度预算快照。 */
    fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot>
}
