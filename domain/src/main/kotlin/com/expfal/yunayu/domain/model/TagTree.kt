package com.expfal.yunayu.domain.model

/** 标签树快照：根标签列表与各根下的子标签映射。 */
data class TagTree(
    val roots: List<Tag>,
    val childrenByRoot: Map<Long, List<Tag>>,
)

/** 将扁平标签列表按父子关系分组为 [TagTree]。 */
fun List<Tag>.toTagTree(): TagTree {
    val roots = filter { it.parentId == null }.sortedBy { it.sortOrder }
    val childrenByRoot = filter { it.parentId != null }
        .groupBy { it.parentId!! }
        .mapValues { (_, tags) -> tags.sortedBy { it.sortOrder } }
    return TagTree(roots = roots, childrenByRoot = childrenByRoot)
}
