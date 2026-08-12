package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [TagRepository] 的 Room 实现。 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeChildren(parentId: Long?): Flow<List<Tag>> =
        tagDao.observeChildren(parentId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getChildren(parentId: Long?): List<Tag> =
        tagDao.getChildren(parentId).map { it.toDomain() }

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
