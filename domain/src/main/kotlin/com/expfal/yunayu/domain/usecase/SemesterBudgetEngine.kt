package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.BudgetPhase
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.Semester
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 学期预算引擎接口（PRD P0-2，签名草图见 SCAFFOLD.md 第 5 节）。
 *
 * 核心算法：周额度 = (剩余总额 ÷ 剩余天数) × 7；月额度按同一日均值 × 30 推导。
 * 额度不落库，由 Flow.combine（学期信息 + 已花费聚合）实时推导，保证数据单一事实来源。
 * 引擎只产出数据不产出文案，文案遵循 PRD 温和提醒原则。
 */
interface SemesterBudgetEngine {

    /** 观察指定学期在 `today` 视角下的预算快照。 */
    fun observeBudgetSnapshot(semesterId: Long, today: LocalDate): Flow<BudgetSnapshot>

    /** 周额度 = (剩余总额 ÷ 剩余天数) × 7，金额单位：分。 */
    fun calcWeeklyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long

    /** 月额度按同一日均值 × 30 推导，金额单位：分。 */
    fun calcMonthlyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long

    /** 判定 `date` 处于学期的哪个预算阶段（常规 / 考试周 / 假期）。 */
    fun resolvePhase(semester: Semester, date: LocalDate): BudgetPhase
}
