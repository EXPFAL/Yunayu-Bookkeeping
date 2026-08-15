package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** 学业关联标签仓储接口，由 :data 模块实现。 */
interface TagRepository {

    /** 观察指定父节点下的子标签（根节点传 `null`），按 sortOrder 升序。 */
    fun observeChildren(parentId: Long?): Flow<List<Tag>>

    /** 一次性获取指定父节点下的子标签（根节点传 `null`）。 */
    suspend fun getChildren(parentId: Long?): List<Tag>

    /**
     * 按起始时间之后、指定收支方向的交易频次降序返回已用标签（最多 [limit] 个）。
     * [type] 为收支过滤方向，收入与支出各自独立统计，避免推荐语境污染。
     */
    suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag>

    /** 拖拽排序后整层重写 sortOrder。 */
    suspend fun updateSortOrder(tags: List<Tag>)

    /**
     * 在指定父标签下新增子标签，返回新标签 id。
     *
     * 同一父节点下不允许重名，命中时抛 [DuplicateTagNameException]；[parentId] 为非空
     * `Long`（类型层面禁止创建根标签）。
     */
    suspend fun addSubTag(parentId: Long, name: String, icon: String? = null): Long

    /**
     * 重命名标签；根标签拒绝（抛 [IllegalArgumentException]），同父重名抛
     * [DuplicateTagNameException]，空名拒绝。
     */
    suspend fun renameTag(tagId: Long, newName: String)

    /** 计算删除 [tagId] 的影响面（子树节点数与受影响的交易数）；目标不存在抛 [IllegalArgumentException]。 */
    suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact

    /** 删除标签；根标签拒绝（抛 [IllegalArgumentException]），子树级联删除与交易置空由 DB 外键执行。 */
    suspend fun deleteTag(tagId: Long)
}
