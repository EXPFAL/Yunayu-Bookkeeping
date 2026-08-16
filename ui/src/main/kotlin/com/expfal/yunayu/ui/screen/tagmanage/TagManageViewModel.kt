package com.expfal.yunayu.ui.screen.tagmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.MergeCandidate
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.FindMergeCandidatesUseCase
import com.expfal.yunayu.domain.usecase.MergeTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 标签整合方向选择：决定候选对中的保留与丢弃方向。 */
enum class MergeChoice {
    A_INTO_B,
    B_INTO_A,
    KEEP_BOTH,
}

/** 标签管理屏 UI 状态快照。 */
data class TagManageUiState(
    val loading: Boolean = true,
    val roots: List<Tag> = emptyList(),
    val childrenByRoot: Map<Long, List<Tag>> = emptyMap(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val pendingDelete: Pair<Tag, TagDeleteImpact>? = null,
    val renamingTag: Tag? = null,
    val mergeDetecting: Boolean = false,
    val mergeDetectFailed: Boolean = false,
    val mergeCandidates: List<MergeCandidate> = emptyList(),
    val mergeChoices: Map<String, MergeChoice> = emptyMap(),
    val merging: Boolean = false,
    val mergeFailed: Boolean = false,
)

/** 标签管理屏对外暴露的一次性事件。 */
sealed interface TagManageEvent {

    /** 标签删除成功，列表由观察链自动刷新。 */
    data object Deleted : TagManageEvent

    /** 删除或排序持久化失败，提示 UI 温和反馈。 */
    data object Failed : TagManageEvent

    /** 标签合并成功（携带被迁移交易数与保留标签名），列表由观察链自动刷新。 */
    data class Merged(
        val affectedTransactionCount: Int,
        val keepTagName: String,
    ) : TagManageEvent

    /** 标签合并失败，提示 UI 重试。 */
    data object MergeFailed : TagManageEvent
}

/** 观察链组装结果：根标签与其各根下的子标签映射。 */
private data class TagTree(
    val roots: List<Tag>,
    val childrenByRoot: Map<Long, List<Tag>>,
)

/**
 * 「学业关联标签」管理 ViewModel：观察根标签与各根下的子标签树，承载子标签增删改与拖拽排序。
 *
 * 观察链以 `observeChildren(null)` 为根，经 [flatMapLatest] 展开每个根节点各自的子标签流，
 * 用 [combine] 组装为 `Map<Long, List<Tag>>`；任一子流异常由 `.catch` 兜底并置 loading=false。
 * 变更动作统一经 `busy` 防重入，失败映射为 [TagManageUiState.errorMessage] 或一次性
 * [TagManageEvent.Failed] 事件；删除采用「先算影响面、再二次确认」两段式。
 * 整合采用「检测候选 → 三选 → 逐对确认」状态机，检测与合并各自独立 Job 且防重入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagManageViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val findMergeCandidatesUseCase: FindMergeCandidatesUseCase,
    private val mergeTagsUseCase: MergeTagsUseCase,
    private val parser: NLTransactionParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManageUiState())
    val uiState: StateFlow<TagManageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TagManageEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<TagManageEvent> = _events.asSharedFlow()

    /** 候选检测任务句柄，[onCleared] 取消。 */
    private var detectJob: Job? = null

    init {
        viewModelScope.launch {
            observeTagTree()
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe tag tree", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { tree ->
                    _uiState.update { prev ->
                        prev.copy(
                            loading = false,
                            roots = tree.roots,
                            childrenByRoot = tree.childrenByRoot,
                            busy = prev.busy,
                        )
                    }
                }
        }
    }

    /**
     * 在指定根 [rootId] 下新增子标签；重名映射「同名标签已存在」，空名等非法入参透出仓储文案；
     * 成功后清空错误反馈（输入框由 UI 关闭弹窗时清空）。
     */
    fun addSubTag(rootId: Long, name: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { tagRepository.addSubTag(rootId, name) }
                .onSuccess { _uiState.update { it.copy(busy = false) } }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to add sub tag") }
        }
    }

    /** 进入改名态：记录待改名标签，供 UI 弹出 [RenameDialog]。 */
    fun requestRename(tag: Tag) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(renamingTag = tag, errorMessage = null) }
    }

    /** 取消改名态，清空改名相关错误反馈。 */
    fun dismissRename() {
        _uiState.update { it.copy(renamingTag = null, errorMessage = null) }
    }

    /** 提交改名：成功后关闭改名态；失败保留改名态并透出错误文案。 */
    fun rename(tagId: Long, newName: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { tagRepository.renameTag(tagId, newName) }
                .onSuccess { _uiState.update { it.copy(busy = false, renamingTag = null) } }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to rename tag") }
        }
    }

    /** 计算删除影响面并置入 [TagManageUiState.pendingDelete]，供 UI 二次确认。 */
    fun requestDelete(tag: Tag) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { tagRepository.getDeleteImpact(tag.id) }
                .onSuccess { impact -> _uiState.update { it.copy(busy = false, pendingDelete = tag to impact) } }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to compute delete impact") }
        }
    }

    /** 确认删除：执行后清空 pendingDelete 并发出 [TagManageEvent.Deleted]；失败发 [TagManageEvent.Failed]。 */
    fun confirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { tagRepository.deleteTag(pending.first.id) }
                .onSuccess {
                    _events.tryEmit(TagManageEvent.Deleted)
                    _uiState.update { it.copy(busy = false, pendingDelete = null, errorMessage = null) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to delete tag", throwable)
                    _events.tryEmit(TagManageEvent.Failed)
                    _uiState.update { it.copy(busy = false, pendingDelete = null, errorMessage = "删除失败，请重试") }
                }
        }
    }

    /** 取消删除确认，清空 [TagManageUiState.pendingDelete]。 */
    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * 拖拽排序提交：先乐观更新 `childrenByRoot` 再经仓储整层重写 sortOrder；
     * 持久化失败回滚到排序前状态并透出「排序保存失败」。
     */
    fun onReorder(parentId: Long, reordered: List<Tag>) {
        if (_uiState.value.busy) return
        val previous = _uiState.value.childrenByRoot
        _uiState.update {
            it.copy(childrenByRoot = it.childrenByRoot + (parentId to reordered), busy = true, errorMessage = null)
        }
        viewModelScope.launch {
            runCatching { tagRepository.updateSortOrder(reordered) }
                .onSuccess { _uiState.update { it.copy(busy = false) } }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to persist sort order", throwable)
                    _uiState.update {
                        it.copy(busy = false, childrenByRoot = previous, errorMessage = "排序保存失败")
                    }
                }
        }
    }

    /** 清空错误反馈（关闭弹窗时调用）。 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 检测疑似重复标签对：mergeDetecting 守卫 + 可取消 [detectJob]；先前置检查引擎可用性
     * （不可用置 [TagManageUiState.mergeDetectFailed]），再经 [FindMergeCandidatesUseCase] 写候选；
     * 异常降级空列表并记录日志，[CancellationException] 直接重抛。
     */
    fun detectMergeCandidates() {
        if (_uiState.value.mergeDetecting) return
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mergeDetecting = true,
                    mergeDetectFailed = false,
                    mergeCandidates = emptyList(),
                    mergeChoices = emptyMap(),
                    mergeFailed = false,
                )
            }
            val available = runCatching { parser.isAvailable() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to check engine availability", throwable)
                }
                .getOrDefault(false)
            if (!available) {
                _uiState.update { it.copy(mergeDetecting = false, mergeDetectFailed = true) }
                return@launch
            }
            val candidates = runCatching { findMergeCandidatesUseCase() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to detect merge candidates", throwable)
                }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(mergeDetecting = false, mergeCandidates = candidates) }
        }
    }

    /** 记录某候选对的用户整合方向；未选择过按 [MergeChoice.KEEP_BOTH] 处理。 */
    fun setMergeChoice(candidate: MergeCandidate, choice: MergeChoice) {
        _uiState.update { state ->
            state.copy(mergeChoices = state.mergeChoices + (candidateKey(candidate) to choice))
        }
    }

    /**
     * 按用户选择合并单个候选对：merging 防重入后依据 [MergeChoice] 决定保留/丢弃方向
     * （A_INTO_B 保留 B 丢弃 A，与引擎 `A_INTO_B` 语义一致），成功发 [TagManageEvent.Merged]
     * 并移除该对；校验或持久化失败发 [TagManageEvent.MergeFailed]，[CancellationException] 重抛。
     */
    fun confirmMerge(candidate: MergeCandidate) {
        if (_uiState.value.merging) return
        val choice = _uiState.value.mergeChoices[candidateKey(candidate)] ?: MergeChoice.KEEP_BOTH
        val keep = when (choice) {
            MergeChoice.A_INTO_B -> candidate.tagB
            MergeChoice.B_INTO_A -> candidate.tagA
            MergeChoice.KEEP_BOTH -> null
        }
        val drop = when (choice) {
            MergeChoice.A_INTO_B -> candidate.tagA
            MergeChoice.B_INTO_A -> candidate.tagB
            MergeChoice.KEEP_BOTH -> null
        }
        if (keep == null || drop == null) return
        _uiState.update { it.copy(merging = true, mergeFailed = false) }
        viewModelScope.launch {
            runCatching { mergeTagsUseCase(keep.id, drop.id) }
                .onSuccess { result ->
                    _events.tryEmit(TagManageEvent.Merged(result.affectedTransactionCount, keep.name))
                    _uiState.update { state ->
                        state.copy(
                            merging = false,
                            mergeCandidates = state.mergeCandidates.filterNot { it == candidate },
                            mergeChoices = state.mergeChoices - candidateKey(candidate),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to merge tags", throwable)
                    _events.tryEmit(TagManageEvent.MergeFailed)
                    _uiState.update { it.copy(merging = false, mergeFailed = true) }
                }
        }
    }

    override fun onCleared() {
        detectJob?.cancel()
        super.onCleared()
    }

    /** 组装根标签与各根子标签的观察链；根为空时直接发射空映射。 */
    private fun observeTagTree(): Flow<TagTree> =
        tagRepository.observeChildren(null).flatMapLatest { roots ->
            if (roots.isEmpty()) {
                flowOf(TagTree(roots, emptyMap()))
            } else {
                val childFlows: List<Flow<Pair<Long, List<Tag>>>> = roots.map { root ->
                    tagRepository.observeChildren(root.id).map { children -> root.id to children }
                }
                combine(childFlows) { lists -> TagTree(roots, lists.toMap()) }
            }
        }

    /** 变更动作统一失败处理：CancellationException 重抛，其余映射错误文案并复位 busy。 */
    private fun handleActionFailure(throwable: Throwable, logMessage: String) {
        if (throwable is CancellationException) throw throwable
        Log.e(TAG, logMessage, throwable)
        _uiState.update { it.copy(busy = false, errorMessage = tagErrorMessage(throwable)) }
    }

    /** 将仓储异常映射为面向用户的温和文案。 */
    private fun tagErrorMessage(throwable: Throwable): String = when (throwable) {
        is DuplicateTagNameException -> "同名标签已存在"
        is IllegalArgumentException -> throwable.message ?: "操作不合法"
        else -> "操作失败，请重试"
    }

    private companion object {
        const val TAG = "TagManageViewModel"
    }
}

/** 候选对的稳定键，按两标签 id 拼接，供 [TagManageUiState.mergeChoices] 索引。 */
private fun candidateKey(candidate: MergeCandidate): String =
    "${candidate.tagA.id}:${candidate.tagB.id}"
