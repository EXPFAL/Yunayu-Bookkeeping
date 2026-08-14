package com.expfal.yunayu.ui.screen.tagmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** 标签管理屏 UI 状态快照。 */
data class TagManageUiState(
    val loading: Boolean = true,
    val roots: List<Tag> = emptyList(),
    val childrenByRoot: Map<Long, List<Tag>> = emptyMap(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val pendingDelete: Pair<Tag, TagDeleteImpact>? = null,
    val renamingTag: Tag? = null,
)

/** 标签管理屏对外暴露的一次性事件。 */
sealed interface TagManageEvent {

    /** 标签删除成功，列表由观察链自动刷新。 */
    data object Deleted : TagManageEvent

    /** 删除或排序持久化失败，提示 UI 温和反馈。 */
    data object Failed : TagManageEvent
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagManageViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManageUiState())
    val uiState: StateFlow<TagManageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TagManageEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<TagManageEvent> = _events.asSharedFlow()

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
