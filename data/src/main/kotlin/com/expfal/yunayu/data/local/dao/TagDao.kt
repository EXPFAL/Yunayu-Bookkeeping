package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.expfal.yunayu.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** 标签 DAO（需求点见 SCAFFOLD.md 4.4）。 */
@Dao
interface TagDao {

    /** 按 parentId 查子节点；根节点查询传 `parentId = null`，按 sortOrder 升序。 */
    @Query("SELECT * FROM tags WHERE parent_id IS :parentId ORDER BY sort_order ASC")
    fun observeChildren(parentId: Long?): Flow<List<TagEntity>>

    /** 按 parentId 一次性查子节点（种子化校验等场景使用）。 */
    @Query("SELECT * FROM tags WHERE parent_id IS :parentId ORDER BY sort_order ASC")
    suspend fun getChildren(parentId: Long?): List<TagEntity>

    /** 拖拽排序后整层重写 sortOrder：传入重排后的完整同级标签列表。 */
    @Update
    suspend fun updateSortOrder(tags: List<TagEntity>)

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Insert
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    /** 全量加载标签（影响面计算时用于在内存中构建父子邻接表）。 */
    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    /** 统计指定父节点下同名标签数量（重名校验，父节点为根不适用）。 */
    @Query("SELECT COUNT(*) FROM tags WHERE parent_id = :parentId AND name = :name")
    suspend fun countByName(parentId: Long, name: String): Int

    /** 取指定父节点下下一个可用 sortOrder（无子节点时从 0 开始）。 */
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM tags WHERE parent_id = :parentId")
    suspend fun nextSortOrder(parentId: Long): Int

    /** 按 id 重命名标签并更新 updatedAt，返回受影响行数（0 表示目标不存在）。 */
    @Query("UPDATE tags SET name = :name, updated_at = :updatedAt WHERE id = :tagId")
    suspend fun renameById(tagId: Long, name: String, updatedAt: Long): Int

    /** 按 id 删除标签（子树级联由外键 ON DELETE CASCADE 执行）。 */
    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteById(tagId: Long)
}
