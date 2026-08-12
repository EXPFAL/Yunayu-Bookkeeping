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
}
