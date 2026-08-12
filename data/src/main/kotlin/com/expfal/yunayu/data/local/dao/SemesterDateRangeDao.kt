package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.SemesterDateRangeEntity
import kotlinx.coroutines.flow.Flow

/** 学期区间 DAO（Schema v2，见 SCAFFOLD.md「Schema v2 增强记录」）。 */
@Dao
interface SemesterDateRangeDao {

    /** 观察指定学期下的全部区间，按起始日期升序。 */
    @Query("SELECT * FROM date_ranges WHERE semester_id = :semesterId ORDER BY start_date ASC")
    fun observeBySemester(semesterId: Long): Flow<List<SemesterDateRangeEntity>>

    /** 批量写入区间（新增或更新学期时使用）。 */
    @Insert
    suspend fun insertAll(ranges: List<SemesterDateRangeEntity>)

    /**
     * 删除指定学期下给定 rangeType 的区间（更新语义：先删后重写）。
     *
     * 调用方仅传入已知类型（EXAM_WEEK / VACATION），以保护未来新增类型行不被
     * 「读侧丢弃」后遭永久删除。
     */
    @Query("DELETE FROM date_ranges WHERE semester_id = :semesterId AND range_type IN (:rangeTypes)")
    suspend fun deleteBySemesterIdAndRangeTypes(semesterId: Long, rangeTypes: List<String>)
}
