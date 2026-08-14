package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.expfal.yunayu.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

/** 学期基础 DAO。 */
@Dao
interface SemesterDao {

    @Insert
    suspend fun insert(semester: SemesterEntity): Long

    /** 按主键更新，返回受影响行数（目标行不存在时返回 0）。 */
    @Update
    suspend fun update(semester: SemesterEntity): Int

    /** 观察全部学期，按起始日期倒序。 */
    @Query("SELECT * FROM semesters ORDER BY start_date DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    /** 按主键观察单个学期；目标行不存在时发射 null。 */
    @Query("SELECT * FROM semesters WHERE id = :id")
    fun observeById(id: Long): Flow<SemesterEntity?>
}
