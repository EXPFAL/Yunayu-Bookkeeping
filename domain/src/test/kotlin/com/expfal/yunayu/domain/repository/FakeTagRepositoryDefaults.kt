package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TagTree
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.toTagTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 测试用 [TagRepository] 基类：为新方法提供空实现，子类按需 override。 */
open class FakeTagRepositoryDefaults : TagRepository {
    override fun observeTagTree(): Flow<TagTree> = flowOf(emptyList<Tag>().toTagTree())

    override suspend fun getAllTags(): List<Tag> = emptyList()

    override suspend fun countTransactionsByTagId(tagId: Long): Int = 0

    override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

    override suspend fun getChildren(parentId: Long?): List<Tag> = emptyList()

    override suspend fun getRecentUsedTags(
        sinceEpochMillis: Long,
        type: TransactionType,
        limit: Int,
    ): List<Tag> = emptyList()

    override suspend fun updateSortOrder(tags: List<Tag>) = Unit

    override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

    override suspend fun addRootTag(name: String, icon: String?): Long = 0L

    override suspend fun renameTag(tagId: Long, newName: String) = Unit

    override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
        TagDeleteImpact(0, 0, emptyList())

    override suspend fun deleteTag(tagId: Long) = Unit

    override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
}
