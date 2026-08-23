package com.expfal.yunayu.ui.screen.subscription

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.SubscriptionBillingCycle
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagTree
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.PostSubscriptionChargeUseCase
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionManageViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneId = ZoneId.systemDefault()

    @Test
    fun `computes monthly total from active subscriptions only`() = runTest {
        val repo = FakeSubscriptionRepository().apply {
            flow.value = listOf(
                sub(1L, "A", 12_000L, SubscriptionBillingCycle.YEARLY, active = true),
                sub(2L, "B", 3_000L, SubscriptionBillingCycle.MONTHLY, active = true),
                sub(3L, "C", 12_000L, SubscriptionBillingCycle.YEARLY, active = false),
            )
        }
        val viewModel = SubscriptionManageViewModel(repo, FakeAccountRepository(), noopPostChargeUseCase())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(2, state.activeCount)
        assertEquals(1_000L + 3_000L, state.totalMonthlyCents)
    }

    @Test
    fun `counts due subscriptions within window`() = runTest {
        val today = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val repo = FakeSubscriptionRepository().apply {
            flow.value = listOf(
                sub(1L, "Due", 1_000L, SubscriptionBillingCycle.MONTHLY, active = true, billingStartAt = today),
                sub(
                    2L,
                    "Later",
                    1_000L,
                    SubscriptionBillingCycle.MONTHLY,
                    active = true,
                    billingStartAt = today + 30L * 86_400_000L,
                ),
            )
        }
        val viewModel = SubscriptionManageViewModel(repo, FakeAccountRepository(), noopPostChargeUseCase())
        runCurrent()

        assertEquals(1, viewModel.uiState.value.dueCount)
    }

    private fun sub(
        id: Long,
        name: String,
        amount: Long,
        cycle: SubscriptionBillingCycle,
        active: Boolean,
        billingStartAt: Long = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli(),
    ) = Subscription(
        id = id,
        name = name,
        amountCents = amount,
        billingCycle = cycle,
        isActive = active,
        billingStartAt = billingStartAt,
    )

    private fun noopPostChargeUseCase(): PostSubscriptionChargeUseCase = PostSubscriptionChargeUseCase(
        object : SubscriptionRepository {
            override fun observeAll(): Flow<List<Subscription>> = flowOf(emptyList())
            override suspend fun getById(id: Long): Subscription? = null
            override suspend fun add(subscription: Subscription): Long = 0L
            override suspend fun update(subscription: Subscription) = Unit
            override suspend fun delete(id: Long) = Unit
        },
        object : TagRepository {
            override fun observeTagTree(): Flow<TagTree> = flowOf(TagTree(emptyList(), emptyMap()))
            override suspend fun getAllTags() = emptyList<Tag>()
            override suspend fun countTransactionsByTagId(tagId: Long) = 0
            override fun observeChildren(parentId: Long?) = flowOf(emptyList<Tag>())
            override suspend fun getChildren(parentId: Long?) = emptyList<Tag>()
            override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int) = emptyList<Tag>()
            override suspend fun updateSortOrder(tags: List<Tag>) = Unit
            override suspend fun addSubTag(parentId: Long, name: String, icon: String?) = 0L
            override suspend fun addRootTag(name: String, icon: String?) = 0L
            override suspend fun renameTag(tagId: Long, newName: String) = Unit
            override suspend fun getDeleteImpact(tagId: Long) = error("unused")
            override suspend fun deleteTag(tagId: Long) = Unit
            override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
        },
        AddTransactionUseCase(
            object : TransactionRepository {
                override suspend fun add(transaction: Transaction): Long = 0L
                override suspend fun delete(transactionId: Long) = Unit
                override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())
                override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())
                override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)
                override fun observeHeldCents(): Flow<Long> = flowOf(0L)
                override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long) = error("unused")
                override suspend fun getExpenseByCategory(startInclusiveMs: Long, endExclusiveMs: Long) = error("unused")
                override fun observeRecent(limit: Int) = error("unused")
                override fun observeFiltered(
                    startInclusiveMs: Long?,
                    endExclusiveMs: Long?,
                    tagIds: List<Long>,
                    noteKeyword: String?,
                    accountFilter: com.expfal.yunayu.domain.model.AccountFilter,
                ) = error("unused")
                override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)
                override suspend fun getUncategorized() = error("unused")
                override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit
                override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
                override suspend fun getById(id: Long): Transaction? = null
                override suspend fun updateTransaction(transaction: Transaction) = Unit
            },
        ),
    )

    private class FakeAccountRepository : AccountRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAccounts(): List<Account> = emptyList()
        override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())
        override suspend fun addAccount(name: String): Long = 0L
        override suspend fun addAccount(name: String, initialBalanceCents: Long): Long = 0L
        override suspend fun renameAccount(id: Long, newName: String) = Unit
        override suspend fun updateAccount(id: Long, newName: String, initialBalanceCents: Long) = Unit
        override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact = AccountDeleteImpact(0)
        override suspend fun deleteAccount(id: Long) = Unit
        override fun observeLastUsedAccountId(): Flow<Long?> = flowOf(null)
        override suspend fun saveLastUsedAccountId(id: Long?) = Unit
        override suspend fun updateInitialBalance(accountId: Long, cents: Long) = Unit
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        val flow = MutableStateFlow<List<Subscription>>(emptyList())

        override fun observeAll(): Flow<List<Subscription>> = flow

        override suspend fun getById(id: Long): Subscription? = flow.value.find { it.id == id }

        override suspend fun add(subscription: Subscription): Long = 0L

        override suspend fun update(subscription: Subscription) = Unit

        override suspend fun delete(id: Long) = Unit
    }
}
