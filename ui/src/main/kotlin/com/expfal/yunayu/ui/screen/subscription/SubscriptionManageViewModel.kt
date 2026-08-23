package com.expfal.yunayu.ui.screen.subscription

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.SUBSCRIPTION_DUE_WINDOW_DAYS
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.SubscriptionBillingCycle
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import com.expfal.yunayu.domain.usecase.PostSubscriptionChargeResult
import com.expfal.yunayu.domain.usecase.PostSubscriptionChargeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 订阅管理屏 UI 状态快照。 */
data class SubscriptionManageUiState(
    val loading: Boolean = true,
    val subscriptions: List<Subscription> = emptyList(),
    val totalMonthlyCents: Long = 0L,
    val activeCount: Int = 0,
    val dueCount: Int = 0,
    val dueSubscriptions: List<Subscription> = emptyList(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val editingSubscription: Subscription? = null,
    val pendingDelete: Subscription? = null,
)

/** 订阅管理屏对外暴露的一次性事件。 */
sealed interface SubscriptionManageEvent {

    data object Saved : SubscriptionManageEvent

    data object Deleted : SubscriptionManageEvent

    data class Posted(val name: String) : SubscriptionManageEvent

    data class PostedAll(val count: Int) : SubscriptionManageEvent

    data class Failed(val message: String) : SubscriptionManageEvent
}

/**
 * 「订阅开支」ViewModel：观察订阅列表并计算生效项的月均摊合计；
 * 承载增 / 改 / 删、到期提醒与一键记一笔。
 */
@HiltViewModel
class SubscriptionManageViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val postSubscriptionChargeUseCase: PostSubscriptionChargeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionManageUiState())
    val uiState: StateFlow<SubscriptionManageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SubscriptionManageEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<SubscriptionManageEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            subscriptionRepository.observeAll()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to observe subscriptions", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { subscriptions ->
                    val active = subscriptions.filter { it.isActive }
                    val due = active.filter { it.isDueWithin(SUBSCRIPTION_DUE_WINDOW_DAYS) }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            subscriptions = subscriptions,
                            totalMonthlyCents = active.sumOf { item -> item.monthlyAmortizedCents },
                            activeCount = active.size,
                            dueCount = due.size,
                            dueSubscriptions = due,
                        )
                    }
                }
        }
    }

    fun addSubscription(
        name: String,
        amountCents: Long,
        billingCycle: SubscriptionBillingCycle,
        note: String?,
        isActive: Boolean,
        billingStartAt: Long,
        lastPostedDueAt: Long? = null,
    ) {
        if (_uiState.value.busy) return
        val validationError = validate(name, amountCents)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                subscriptionRepository.add(
                    Subscription(
                        name = name.trim(),
                        amountCents = amountCents,
                        billingCycle = billingCycle,
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        isActive = isActive,
                        billingStartAt = billingStartAt,
                        lastPostedDueAt = lastPostedDueAt,
                    ),
                )
            }
                .onSuccess {
                    _events.tryEmit(SubscriptionManageEvent.Saved)
                    _uiState.update { it.copy(busy = false, errorMessage = null) }
                }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to add subscription") }
        }
    }

    fun requestEdit(subscription: Subscription) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(editingSubscription = subscription, errorMessage = null) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editingSubscription = null, errorMessage = null) }
    }

    fun updateSubscription(
        id: Long,
        name: String,
        amountCents: Long,
        billingCycle: SubscriptionBillingCycle,
        note: String?,
        isActive: Boolean,
        billingStartAt: Long,
        lastPostedDueAt: Long?,
        lastPostedAt: Long?,
        createdAt: Long,
    ) {
        if (_uiState.value.busy) return
        val validationError = validate(name, amountCents)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                subscriptionRepository.update(
                    Subscription(
                        id = id,
                        name = name.trim(),
                        amountCents = amountCents,
                        billingCycle = billingCycle,
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        isActive = isActive,
                        billingStartAt = billingStartAt,
                        lastPostedDueAt = lastPostedDueAt,
                        lastPostedAt = lastPostedAt,
                        createdAt = createdAt,
                    ),
                )
            }
                .onSuccess {
                    _events.tryEmit(SubscriptionManageEvent.Saved)
                    _uiState.update { it.copy(busy = false, editingSubscription = null, errorMessage = null) }
                }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to update subscription") }
        }
    }

    fun postCharge(subscriptionId: Long) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { postSubscriptionChargeUseCase(subscriptionId) }
                .onSuccess { result ->
                    when (result) {
                        is PostSubscriptionChargeResult.Success -> {
                            _events.tryEmit(SubscriptionManageEvent.Posted(result.subscriptionName))
                            _uiState.update { it.copy(busy = false) }
                        }
                        PostSubscriptionChargeResult.NotFound -> {
                            _events.tryEmit(SubscriptionManageEvent.Failed("订阅不存在"))
                            _uiState.update { it.copy(busy = false) }
                        }
                        PostSubscriptionChargeResult.NothingToPost -> {
                            _events.tryEmit(SubscriptionManageEvent.Failed("当前没有待记的扣费"))
                            _uiState.update { it.copy(busy = false) }
                        }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to post subscription charge", throwable)
                    _events.tryEmit(SubscriptionManageEvent.Failed("记一笔失败，请重试"))
                    _uiState.update { it.copy(busy = false) }
                }
        }
    }

    fun postAllDue() {
        val due = _uiState.value.dueSubscriptions
        if (due.isEmpty() || _uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            var posted = 0
            runCatching {
                due.forEach { subscription ->
                    when (postSubscriptionChargeUseCase(subscription.id)) {
                        is PostSubscriptionChargeResult.Success -> posted++
                        PostSubscriptionChargeResult.NotFound,
                        PostSubscriptionChargeResult.NothingToPost,
                        -> Unit
                    }
                }
            }
                .onSuccess {
                    if (posted > 0) {
                        _events.tryEmit(SubscriptionManageEvent.PostedAll(posted))
                    } else {
                        _events.tryEmit(SubscriptionManageEvent.Failed("没有可记的订阅"))
                    }
                    _uiState.update { it.copy(busy = false) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to post all due subscriptions", throwable)
                    _events.tryEmit(SubscriptionManageEvent.Failed("批量记一笔失败，请重试"))
                    _uiState.update { it.copy(busy = false) }
                }
        }
    }

    fun requestDelete(subscription: Subscription) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(pendingDelete = subscription) }
    }

    fun confirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { subscriptionRepository.delete(pending.id) }
                .onSuccess {
                    _events.tryEmit(SubscriptionManageEvent.Deleted)
                    _uiState.update { it.copy(busy = false, pendingDelete = null, errorMessage = null) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to delete subscription", throwable)
                    _events.tryEmit(SubscriptionManageEvent.Failed("删除失败，请重试"))
                    _uiState.update { it.copy(busy = false, pendingDelete = null) }
                }
        }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun validate(name: String, amountCents: Long): String? = when {
        name.isBlank() -> "名称不可为空"
        amountCents <= 0L -> "金额须大于 0"
        else -> null
    }

    private fun handleActionFailure(throwable: Throwable, logMessage: String) {
        if (throwable is CancellationException) throw throwable
        Log.e(TAG, logMessage, throwable)
        _uiState.update {
            it.copy(
                busy = false,
                errorMessage = when (throwable) {
                    is IllegalArgumentException -> throwable.message ?: "操作不合法"
                    else -> "操作失败，请重试"
                },
            )
        }
    }

    private companion object {
        const val TAG = "SubscriptionManageVM"
    }
}
