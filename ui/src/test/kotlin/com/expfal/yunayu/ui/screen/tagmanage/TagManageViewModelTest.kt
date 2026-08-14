package com.expfal.yunayu.ui.screen.tagmanage

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [TagManageViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagManageViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads four root partitions`() = runTest {
        val viewModel = TagManageViewModel(fourRootRepo())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf(1L, 2L, 3L, 4L), state.roots.map { it.id })
        assertEquals(setOf(1L, 2L, 3L, 4L), state.childrenByRoot.keys)
    }

    @Test
    fun `addSubTag success clears error`() = runTest {
        val repo = fourRootRepo()
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "教材")
        runCurrent()

        assertEquals(listOf(Triple(1L, "教材", null)), repo.added)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `addSubTag duplicate name maps friendly error`() = runTest {
        val repo = fourRootRepo().apply { addError = DuplicateTagNameException("dup") }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "教材")
        runCurrent()

        assertEquals("同名标签已存在", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `addSubTag illegal argument maps repository message`() = runTest {
        val repo = fourRootRepo().apply { addError = IllegalArgumentException("标签名不可为空") }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "")
        runCurrent()

        assertEquals("标签名不可为空", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `rename succeeds and closes rename state`() = runTest {
        val repo = fourRootRepo()
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        viewModel.requestRename(tag(5, "旧名", parentId = 1L))
        viewModel.rename(5, "新名")
        runCurrent()

        assertEquals(listOf(5L to "新名"), repo.renamed)
        assertNull(viewModel.uiState.value.renamingTag)
    }

    @Test
    fun `rename duplicate name keeps rename state with error`() = runTest {
        val repo = fourRootRepo().apply { renameError = DuplicateTagNameException("dup") }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        viewModel.requestRename(tag(5, "旧名", parentId = 1L))
        viewModel.rename(5, "教材")
        runCurrent()

        assertEquals("同名标签已存在", viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.renamingTag)
    }

    @Test
    fun `requestDelete computes impact into pendingDelete`() = runTest {
        val repo = fourRootRepo().apply { impactResult = TagDeleteImpact(3, 5, listOf("a", "b", "c")) }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        val child = tag(5, "教材", parentId = 1L)
        viewModel.requestDelete(child)
        runCurrent()

        assertEquals(child, viewModel.uiState.value.pendingDelete?.first)
        assertEquals(3, viewModel.uiState.value.pendingDelete?.second?.subtreeNodeCount)
        assertEquals(5, viewModel.uiState.value.pendingDelete?.second?.affectedTransactionCount)
    }

    @Test
    fun `confirmDelete deletes clears state and emits Deleted`() = runTest {
        val repo = fourRootRepo()
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        val events = mutableListOf<TagManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.requestDelete(tag(5, "教材", parentId = 1L))
        runCurrent()
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf(5L), repo.deletedIds)
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(listOf(TagManageEvent.Deleted), events)
    }

    @Test
    fun `cancelDelete clears pendingDelete`() = runTest {
        val viewModel = TagManageViewModel(fourRootRepo())
        runCurrent()

        viewModel.requestDelete(tag(5, "教材", parentId = 1L))
        runCurrent()
        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun `onReorder optimistically updates then rolls back on failure`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        val original = viewModel.uiState.value.childrenByRoot.getValue(1L)
        val gate = CompletableDeferred<Unit>()
        repo.sortGate = gate

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))

        gate.completeExceptionally(RuntimeException("db down"))
        runCurrent()

        assertEquals(original, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertEquals("排序保存失败", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onReorder success keeps new order`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)
        runCurrent()

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `onReorder success converges with observation flow pushing new order`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = TagManageViewModel(repo)
        runCurrent()

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)
        runCurrent()

        // 模拟持久化成功后观察链重发射新序，最终态应与提交的新序一致
        repo.childrenFlows.getValue(1L).value = reordered
        runCurrent()

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertFalse(viewModel.uiState.value.busy)
    }

    private fun fourRootRepo(): FakeTagRepository {
        val repo = FakeTagRepository()
        val roots = listOf(tag(1, "学习"), tag(2, "社交"), tag(3, "生活"), tag(4, "娱乐"))
        repo.rootsFlow.value = roots
        roots.forEach { repo.childrenFlows[it.id] = MutableStateFlow(emptyList()) }
        return repo
    }

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(id = id, name = name, parentId = parentId)

    /** [TagRepository] 手写 fake：以 MutableStateFlow 驱动树观察，记录变更入参并可配置异常/挂起。 */
    private class FakeTagRepository : TagRepository {

        val rootsFlow = MutableStateFlow<List<Tag>>(emptyList())
        val childrenFlows = mutableMapOf<Long, MutableStateFlow<List<Tag>>>()

        var addError: Throwable? = null
        var renameError: Throwable? = null
        var deleteImpactError: Throwable? = null
        var deleteError: Throwable? = null
        var sortGate: CompletableDeferred<Unit>? = null
        var sortError: Throwable? = null

        val added = mutableListOf<Triple<Long, String, String?>>()
        val renamed = mutableListOf<Pair<Long, String>>()
        val deletedIds = mutableListOf<Long>()
        var impactResult = TagDeleteImpact(1, 0, emptyList())

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> =
            if (parentId == null) rootsFlow else childrenFlows.getOrPut(parentId) { MutableStateFlow(emptyList()) }

        override suspend fun getChildren(parentId: Long?): List<Tag> = emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) {
            sortError?.let { throw it }
            sortGate?.let { it.await() }
        }

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
            addError?.let { throw it }
            added += Triple(parentId, name, icon)
            return 100L
        }

        override suspend fun renameTag(tagId: Long, newName: String) {
            renameError?.let { throw it }
            renamed += tagId to newName
        }

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact {
            deleteImpactError?.let { throw it }
            return impactResult
        }

        override suspend fun deleteTag(tagId: Long) {
            deleteError?.let { throw it }
            deletedIds += tagId
        }
    }
}
