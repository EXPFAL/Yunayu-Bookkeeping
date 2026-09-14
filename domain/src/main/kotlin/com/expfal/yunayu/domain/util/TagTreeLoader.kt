package com.expfal.yunayu.domain.util

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException

/**
 * 标签树加载：根 → 子标签分组，供快捷记账「更多分类」、收支筛选、整理候选等复用。
 */
object TagTreeLoader {

    /**
     * 加载根标签及其子标签。
     *
     * @param rootFilter 非空时过滤根列表（如按收支方向）；为 null 时保留全部根。
     * @param onChildFailure 某根的子标签加载失败时回调（取消异常仍会重抛）；默认忽略并记为空子列表。
     */
    suspend fun loadByRoot(
        tagRepository: TagRepository,
        rootFilter: ((Tag) -> Boolean)? = null,
        onChildFailure: (rootId: Long, throwable: Throwable) -> Unit = { _, _ -> },
    ): Map<Tag, List<Tag>> {
        val roots = tagRepository.getChildren(parentId = null)
            .let { list -> rootFilter?.let { list.filter(it) } ?: list }
        return roots.associateWith { root ->
            runCatching { tagRepository.getChildren(parentId = root.id) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    onChildFailure(root.id, throwable)
                }
                .getOrDefault(emptyList())
        }
    }

    /** 根 id → 根名，供「父·子」展示。 */
    suspend fun loadRootNameById(tagRepository: TagRepository): Map<Long, String> =
        tagRepository.getChildren(parentId = null).associate { it.id to it.name }

    /**
     * 标签展示名：根或父名缺失时返回自身名；子标签返回「父·子」。
     * 与 UI [com.expfal.yunayu.ui.util.tagDisplayName] 语义一致，供 domain / 多屏复用。
     */
    fun displayName(tag: Tag, rootNameById: Map<Long, String>): String {
        val parentId = tag.parentId ?: return tag.name
        val parentName = rootNameById[parentId] ?: return tag.name
        return "$parentName·${tag.name}"
    }

    /**
     * 本地疑似重复叶子对数：同名叶子（不同 id）按组合计数，供整理完成页 hint，避免再打 LLM。
     */
    fun countDuplicateNameLeafPairs(tagsByRoot: Map<Tag, List<Tag>>): Int {
        val leaves = tagsByRoot.values.flatten()
        val byName = leaves.groupBy { it.name }.filterValues { it.size >= 2 }
        var pairs = 0
        for (group in byName.values) {
            val n = group.size
            pairs += n * (n - 1) / 2
        }
        return pairs
    }
}
