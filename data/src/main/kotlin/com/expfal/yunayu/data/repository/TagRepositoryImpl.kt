package com.expfal.yunayu.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [TagRepository] 的 Room 实现。 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
    private val tagMergeExecutor: TagMergeExecutor,
) : TagRepository {

    override fun observeChildren(parentId: Long?): Flow<List<Tag>> =
        tagDao.observeChildren(parentId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getChildren(parentId: Long?): List<Tag> =
        tagDao.getChildren(parentId).map { it.toDomain() }

    override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> =
        transactionDao.getRecentFrequentTags(sinceEpochMillis, type.name, limit).map { it.tag.toDomain() }

    override suspend fun updateSortOrder(tags: List<Tag>) {
        val now = System.currentTimeMillis()
        tagDao.updateSortOrder(
            tags.mapIndexed { index, tag -> tag.toEntity(sortOrder = index, updatedAt = now) },
        )
    }

    override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不可为空" }
        return try {
            if (tagDao.countByName(parentId, trimmed) > 0) {
                throw DuplicateTagNameException("父标签 $parentId 下已存在同名标签「$trimmed」")
            }
            val sortOrder = tagDao.nextSortOrder(parentId)
            val now = System.currentTimeMillis()
            tagDao.insert(
                TagEntity(
                    id = 0L,
                    name = trimmed,
                    parentId = parentId,
                    sortOrder = sortOrder,
                    icon = icon,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "Constraint violation while adding sub tag", e)
            if (e.message?.contains("UNIQUE") == true) {
                throw DuplicateTagNameException("父标签 $parentId 下已存在同名标签「$trimmed」")
            }
            throw e
        }
    }

    override suspend fun addRootTag(name: String, icon: String?): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不可为空" }
        require(trimmed == IncomeTags.INCOME_ROOT_NAME) { "仅支持预置根类" }
        val all = tagDao.getAll()
        if (all.any { it.parentId == null && it.name == trimmed }) {
            throw DuplicateTagNameException("根标签「$trimmed」已存在")
        }
        val sortOrder = all.count { it.parentId == null }
        val now = System.currentTimeMillis()
        return tagDao.insert(
            TagEntity(
                id = 0L,
                name = trimmed,
                parentId = null,
                sortOrder = sortOrder,
                icon = icon,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun renameTag(tagId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("标签名不可为空")
        val all = tagDao.getAll()
        val target = all.firstOrNull { it.id == tagId }
            ?: throw IllegalArgumentException("标签不存在：$tagId")
        if (target.parentId == null) throw IllegalArgumentException("根标签不可重命名")
        if (all.any { it.id != tagId && it.parentId == target.parentId && it.name == trimmed }) {
            throw DuplicateTagNameException("父标签 ${target.parentId} 下已存在同名标签「$trimmed」")
        }
        val affected = tagDao.renameById(tagId, trimmed, System.currentTimeMillis())
        if (affected == 0) throw IllegalArgumentException("标签不存在：$tagId")
    }

    override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact {
        val all = tagDao.getAll()
        val byId = all.associateBy { it.id }
        if (byId[tagId] == null) throw IllegalArgumentException("标签不存在：$tagId")
        val childrenByParent = all.groupBy { it.parentId }
        val visited = linkedSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(tagId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            childrenByParent[current]?.forEach { queue.add(it.id) }
        }
        require(visited.isNotEmpty()) { "删除影响面计算异常：子树为空" }
        val affected = transactionDao.countByTagIds(visited.toList())
        return TagDeleteImpact(
            subtreeNodeCount = visited.size,
            affectedTransactionCount = affected,
            subtreeNames = visited.map { byId.getValue(it).name },
        )
    }

    override suspend fun deleteTag(tagId: Long) {
        val target = tagDao.getAll().firstOrNull { it.id == tagId }
            ?: throw IllegalArgumentException("标签不存在：$tagId")
        if (target.parentId == null) throw IllegalArgumentException("根标签不可删除")
        tagDao.deleteById(tagId)
    }

    override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) {
        if (keepTagId == dropTagId) throw IllegalArgumentException("不可合并到自身")
        val all = tagDao.getAll()
        val byId = all.associateBy { it.id }
        val keep = byId[keepTagId] ?: throw IllegalArgumentException("标签不存在：$keepTagId")
        val drop = byId[dropTagId] ?: throw IllegalArgumentException("标签不存在：$dropTagId")
        if (keep.parentId == null || drop.parentId == null) {
            throw IllegalArgumentException("根标签不可合并")
        }
        // drop 必须为叶子：tags 子树外键 ON DELETE CASCADE，带子级删除会误删整棵子树
        if (all.any { it.parentId == dropTagId }) throw IllegalArgumentException("仅叶子标签可合并")
        tagMergeExecutor.merge(keepTagId, dropTagId)
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

    private companion object {
        const val TAG = "TagRepositoryImpl"
    }
}
