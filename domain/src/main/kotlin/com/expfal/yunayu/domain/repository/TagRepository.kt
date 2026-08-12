package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** 学业关联标签仓储接口，由 :data 模块实现。 */
interface TagRepository {

    /** 观察指定父节点下的子标签（根节点传 `null`），按 sortOrder 升序。 */
    fun observeChildren(parentId: Long?): Flow<List<Tag>>

    /** 一次性获取指定父节点下的子标签（根节点传 `null`）。 */
    suspend fun getChildren(parentId: Long?): List<Tag>

    /** 拖拽排序后整层重写 sortOrder。 */
    suspend fun updateSortOrder(tags: List<Tag>)
}
