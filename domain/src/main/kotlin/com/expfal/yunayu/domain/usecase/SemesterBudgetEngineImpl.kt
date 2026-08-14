package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.BudgetPhase
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.SemesterRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * [SemesterBudgetEngine] 的纯函数实现，仅依赖领域仓储接口，无框架注解。
 *
 * 核心算法（PRD P0-2）：
 * - 周额度 = 剩余总额 ÷ 剩余天数 × 7 × 阶段系数；月额度按同一日均值 × 30 推导。
 * - 为避免浮点误差，阶段系数用整数百分比表示，公式统一为
 *   `remainingCents * 7(或 30) * pct / remainingDays / 100`，金额以「分」为单位逐级向下取整。
 *
 * 默认假设（待用户确认后可由常量调整为可配置项）：
 * 1. 阶段系数：考试周 80（0.8 缩减）、常规 100、假期 110（1.1 上浮）。
 * 2. 月额度按 30 天折算。
 * 3. 学期不存在时发射全零快照（phase=NORMAL），避免下游因空值崩溃。
 */
class SemesterBudgetEngineImpl(
    private val semesterRepository: SemesterRepository,
    private val transactionRepository: TransactionRepository,
) : SemesterBudgetEngine {

    /**
     * 观察指定学期在 `today` 视角下的预算快照。
     *
     * 由 [combine]（学期列表 + 全部交易）实时推导，任一数据源变更即重新发射；
     * 找不到匹配学期时发射全零快照（默认假设，见类级 KDoc）。
     */
    override fun observeBudgetSnapshot(semesterId: Long, today: LocalDate): Flow<BudgetSnapshot> =
        combine(
            semesterRepository.observeAll(),
            transactionRepository.observeAll(),
        ) { semesters, transactions ->
            val semester = semesters.firstOrNull { it.id == semesterId }
            if (semester == null) {
                ZERO_SNAPSHOT
            } else {
                buildSnapshot(semester, transactions, today)
            }
        }

    /**
     * 周额度 = `remainingCents * 7 * pct / remainingDays / 100`，`pct` 为阶段系数百分比。
     * 约定 `remainingDays >= 1`（由快照组装处钳制），金额向下取整。
     */
    override fun calcWeeklyQuota(
        remainingCents: Long,
        remainingDays: Int,
        phase: BudgetPhase,
    ): Long {
        val pct = coefficientPercent(phase)
        return remainingCents * 7 * pct / remainingDays / 100
    }

    /**
     * 月额度 = `remainingCents * 30 * pct / remainingDays / 100`（按 30 天折算，默认假设）。
     * 约定 `remainingDays >= 1`，金额向下取整。
     */
    override fun calcMonthlyQuota(
        remainingCents: Long,
        remainingDays: Int,
        phase: BudgetPhase,
    ): Long {
        val pct = coefficientPercent(phase)
        return remainingCents * 30 * pct / remainingDays / 100
    }

    /**
     * 判定 `date` 所处的预算阶段：考试周（含端点）优先于假期，二者重叠时取考试周（默认假设）。
     */
    override fun resolvePhase(semester: Semester, date: LocalDate): BudgetPhase {
        val inExamWeek = semester.examWeekRanges.any { range ->
            !date.isBefore(range.start) && !date.isAfter(range.endInclusive)
        }
        if (inExamWeek) return BudgetPhase.EXAM_WEEK

        val inVacation = semester.vacationRanges.any { range ->
            !date.isBefore(range.start) && !date.isAfter(range.endInclusive)
        }
        if (inVacation) return BudgetPhase.VACATION

        return BudgetPhase.NORMAL
    }

    /** 由学期、交易与 `today` 组装预算快照。 */
    private fun buildSnapshot(
        semester: Semester,
        transactions: List<Transaction>,
        today: LocalDate,
    ): BudgetSnapshot {
        val spentCents = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .filter { transaction -> isWithinSemester(transaction, semester) }
            .sumOf { it.amountCents }
        val remainingCents = (semester.totalBudgetCents - spentCents).coerceAtLeast(0L)
        val remainingDays = (ChronoUnit.DAYS.between(today, semester.endDate).toInt() + 1)
            .coerceAtLeast(MIN_REMAINING_DAYS)
        val phase = resolvePhase(semester, today)

        return BudgetSnapshot(
            totalBudgetCents = semester.totalBudgetCents,
            spentCents = spentCents,
            remainingCents = remainingCents,
            remainingDays = remainingDays,
            weeklyQuotaCents = calcWeeklyQuota(remainingCents, remainingDays, phase),
            monthlyQuotaCents = calcMonthlyQuota(remainingCents, remainingDays, phase),
            phase = phase,
        )
    }

    /** 交易发生日期是否落在学期区间内（含起止日）。 */
    private fun isWithinSemester(transaction: Transaction, semester: Semester): Boolean {
        val date = Instant.ofEpochMilli(transaction.occurredAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !date.isBefore(semester.startDate) && !date.isAfter(semester.endDate)
    }

    /** 阶段系数（整数百分比）：常规 100、考试周 80、假期 110。 */
    private fun coefficientPercent(phase: BudgetPhase): Int = when (phase) {
        BudgetPhase.NORMAL -> NORMAL_PERCENT
        BudgetPhase.EXAM_WEEK -> EXAM_WEEK_PERCENT
        BudgetPhase.VACATION -> VACATION_PERCENT
    }

    private companion object {
        const val NORMAL_PERCENT = 100
        const val EXAM_WEEK_PERCENT = 80
        const val VACATION_PERCENT = 110
        const val MIN_REMAINING_DAYS = 1

        /** 学期未找到时的全零快照（phase=NORMAL）。 */
        val ZERO_SNAPSHOT = BudgetSnapshot(
            totalBudgetCents = 0L,
            spentCents = 0L,
            remainingCents = 0L,
            remainingDays = 0,
            weeklyQuotaCents = 0L,
            monthlyQuotaCents = 0L,
            phase = BudgetPhase.NORMAL,
        )
    }
}
