package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

/** 学期基础 DAO。 */
@Dao
interface SemesterDao {

    @Insert
    suspend fun insert(semester: SemesterEntity): Long

    /** 观察全部学期，按起始日期倒序。 */
    @Query("SELECT * FROM semesters ORDER BY start_date DESC")
    fun observeAll(): Flow<List<SemesterEntity>>
}
