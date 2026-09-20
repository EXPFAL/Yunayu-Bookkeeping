package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.report.model.CategoryExpenseDetail
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/**
 * 加载报告分类详情：笔数、日均、最近流水；「其他」模式下附带 TopN 之外的剩余分类列表。
 */
class LoadCategoryExpenseDetailUseCase(
    private val transactionRepository: TransactionRepository,
) {

    /**
     * @param share 当前选中的占比项；[isOtherBucket] 为 true 时按 TopN 残差处理。
     * @param totalExpenseCents 报告总支出（分），用于占比与「其他」金额校准。
     * @param windowDayCount 窗口天数（≥1），用于日均。
     */
    suspend operator fun invoke(
        windowStartMs: Long,
        windowEndMs: Long,
        totalExpenseCents: Long,
        windowDayCount: Int,
        share: CategoryShare,
        isOtherBucket: Boolean,
    ): CategoryExpenseDetail {
        val days = windowDayCount.coerceAtLeast(1)
        return if (isOtherBucket) {
            loadOther(windowStartMs, windowEndMs, totalExpenseCents, days)
        } else {
            loadTag(windowStartMs, windowEndMs, days, share)
        }
    }

    private suspend fun loadTag(
        windowStartMs: Long,
        windowEndMs: Long,
        windowDayCount: Int,
        share: CategoryShare,
    ): CategoryExpenseDetail {
        val matched = loadExpensesForShare(windowStartMs, windowEndMs, share)
        val cents = share.cents
        return CategoryExpenseDetail(
            expenseCents = cents,
            percent = share.percent,
            txCount = matched.size,
            dailyAvgCents = cents / windowDayCount,
            recent = matched.take(ReportCategoryLimits.RECENT_TRANSACTIONS),
        )
    }

    private suspend fun loadOther(
        windowStartMs: Long,
        windowEndMs: Long,
        totalExpenseCents: Long,
        windowDayCount: Int,
    ): CategoryExpenseDetail {
        val allCategories = transactionRepository.getExpenseByCategory(windowStartMs, windowEndMs)
        val remaining = allCategories.drop(ReportCategoryLimits.TOP_CATEGORIES)
        val remainingShares = remaining.map {
            CategoryShare(
                tagName = it.tagName,
                cents = it.cents,
                percent = percentOf(it.cents, totalExpenseCents),
                tagId = it.tagId,
            )
        }
        val topSum = allCategories.take(ReportCategoryLimits.TOP_CATEGORIES).sumOf { it.cents }
        val otherCents =
            if (totalExpenseCents > topSum) totalExpenseCents - topSum else remaining.sumOf { it.cents }
        val matched = remainingShares
            .flatMap { loadExpensesForShare(windowStartMs, windowEndMs, it) }
            .distinctBy { it.id }
            .sortedByDescending { it.occurredAt }
        return CategoryExpenseDetail(
            expenseCents = otherCents,
            percent = percentOf(otherCents, totalExpenseCents),
            txCount = matched.size,
            dailyAvgCents = otherCents / windowDayCount,
            recent = matched.take(ReportCategoryLimits.RECENT_TRANSACTIONS),
            remainingShares = remainingShares,
        )
    }

    private suspend fun loadExpensesForShare(
        windowStartMs: Long,
        windowEndMs: Long,
        share: CategoryShare,
    ): List<RecentTransaction> {
        val tagId = share.tagId
        return if (tagId != null) {
            transactionRepository.observeFiltered(
                startInclusiveMs = windowStartMs,
                endExclusiveMs = windowEndMs,
                tagIds = listOf(tagId),
                noteKeyword = null,
                accountFilter = AccountFilter.All,
            ).first().filter { it.type == TransactionType.EXPENSE }
        } else {
            // 未分类：窗口内支出且无标签名
            transactionRepository.observeFiltered(
                startInclusiveMs = windowStartMs,
                endExclusiveMs = windowEndMs,
                tagIds = emptyList(),
                noteKeyword = null,
                accountFilter = AccountFilter.All,
            ).first().filter { it.type == TransactionType.EXPENSE && it.tagName == null }
        }
    }

    private fun percentOf(partCents: Long, totalCents: Long): Int =
        if (totalCents <= 0L) 0 else ((partCents * 100) / totalCents).toInt()
}
