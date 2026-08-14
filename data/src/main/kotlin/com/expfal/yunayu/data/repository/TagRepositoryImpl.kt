package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [TagRepository] 的 Room 实现。
 *
 * TODO(schema-v2): 当引入子标签新增入口（add）时，需在仓储层对「同父节点同名」做显式防御：
 * 唯一索引 (parent_id, name) 对 NULL parent_id 不生效（SQLite 允许多个 NULL 共存），
 * 因此根节点（parentId == null）的同名校验必须在写入前完成并返回明确错误。
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
) : TagRepository {

    override fun observeChildren(parentId: Long?): Flow<List<Tag>> =
        tagDao.observeChildren(parentId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getChildren(parentId: Long?): List<Tag> =
        tagDao.getChildren(parentId).map { it.toDomain() }

    override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> =
        transactionDao.getRecentFrequentTags(sinceEpochMillis, limit).map { it.tag.toDomain() }

    override suspend fun updateSortOrder(tags: List<Tag>) {
        val now = System.currentTimeMillis()
        tagDao.updateSortOrder(
            tags.mapIndexed { index, tag -> tag.toEntity(sortOrder = index, updatedAt = now) },
        )
    }

    private fun TagEntity.toDomain(): Tag = Tag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = sortOrder,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Tag.toEntity(sortOrder: Int, updatedAt: Long): TagEntity = TagEntity(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = sortOrder,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
