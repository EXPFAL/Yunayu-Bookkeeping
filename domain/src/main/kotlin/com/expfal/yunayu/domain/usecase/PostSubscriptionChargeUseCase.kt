package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.repository.TagRepository
import kotlin.math.min

/** [PostSubscriptionChargeUseCase] 执行结果。 */
sealed interface PostSubscriptionChargeResult {

    data class Success(val transactionId: Long, val subscriptionName: String) : PostSubscriptionChargeResult

    data object NotFound : PostSubscriptionChargeResult

    data object NothingToPost : PostSubscriptionChargeResult
}

/**
 * 将一笔订阅扣费记入交易流水，并记录该期对应的扣费日 [Subscription.lastPostedDueAt]。
 *
 * 扣费日由起始日与计费周期推算；自动挂载「学习」根下「订阅」子标签（若存在）。
 */
class PostSubscriptionChargeUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val tagRepository: TagRepository,
    private val addTransactionUseCase: AddTransactionUseCase,
) {

    suspend operator fun invoke(
        subscriptionId: Long,
        accountId: Long? = null,
    ): PostSubscriptionChargeResult {
        val subscription = subscriptionRepository.getById(subscriptionId) ?: return PostSubscriptionChargeResult.NotFound
        val chargeDueAt = subscription.pendingChargeDueAt() ?: return PostSubscriptionChargeResult.NothingToPost
        val occurredAt = min(chargeDueAt, System.currentTimeMillis())
        val tagId = resolveSubscriptionTagId()
        val note = buildNote(subscription)
        val transactionId = addTransactionUseCase(
            amountCents = subscription.amountCents,
            tagId = tagId,
            occurredAt = occurredAt,
            type = TransactionType.EXPENSE,
            accountId = accountId,
            note = note,
        )
        subscriptionRepository.update(
            subscription.copy(
                lastPostedDueAt = chargeDueAt,
                lastPostedAt = System.currentTimeMillis(),
            ),
        )
        return PostSubscriptionChargeResult.Success(
            transactionId = transactionId,
            subscriptionName = subscription.name,
        )
    }

    private suspend fun resolveSubscriptionTagId(): Long? {
        val studyRoot = tagRepository.getChildren(null).find { it.name == STUDY_ROOT_NAME } ?: return null
        return tagRepository.getChildren(studyRoot.id).find { it.name == SUBSCRIPTION_TAG_NAME }?.id
    }

    private fun buildNote(subscription: Subscription): String {
        val base = "订阅：${subscription.name}"
        val extra = subscription.note?.trim().orEmpty()
        return if (extra.isEmpty()) base else "$base（$extra）"
    }

    private companion object {
        const val STUDY_ROOT_NAME = "学习"
        const val SUBSCRIPTION_TAG_NAME = "订阅"
    }
}
