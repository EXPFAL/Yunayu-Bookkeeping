package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.SubscriptionDao
import com.expfal.yunayu.data.local.entity.SubscriptionEntity
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.SubscriptionBillingCycle
import com.expfal.yunayu.domain.model.initialBillingStartAt
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [SubscriptionRepository] 的 Room 实现。 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
) : SubscriptionRepository {

    override fun observeAll(): Flow<List<Subscription>> =
        subscriptionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Subscription? =
        subscriptionDao.getById(id)?.toDomain()

    override suspend fun add(subscription: Subscription): Long {
        val now = System.currentTimeMillis()
        val entity = subscription.toEntity(createdAt = now, updatedAt = now)
        return subscriptionDao.insert(entity.copy(id = 0L))
    }

    override suspend fun update(subscription: Subscription) {
        val now = System.currentTimeMillis()
        subscriptionDao.update(
            subscription.toEntity(
                createdAt = subscription.createdAt,
                updatedAt = now,
            ),
        )
    }

    override suspend fun delete(id: Long) {
        subscriptionDao.deleteById(id)
    }

    private fun Subscription.toEntity(createdAt: Long, updatedAt: Long): SubscriptionEntity =
        SubscriptionEntity(
            id = id,
            name = name.trim(),
            amountCents = amountCents,
            billingCycle = billingCycle.name,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            isActive = isActive,
            billingStartAt = billingStartAt,
            lastPostedDueAt = lastPostedDueAt,
            lastPostedAt = lastPostedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun SubscriptionEntity.toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        amountCents = amountCents,
        billingCycle = runCatching { SubscriptionBillingCycle.valueOf(billingCycle) }
            .getOrDefault(SubscriptionBillingCycle.MONTHLY),
        note = note,
        isActive = isActive,
        billingStartAt = billingStartAt.takeIf { it > 0L } ?: initialBillingStartAt(),
        lastPostedDueAt = lastPostedDueAt,
        lastPostedAt = lastPostedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
