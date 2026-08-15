package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.expfal.yunayu.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

/** 报告基础 DAO。 */
@Dao
interface ReportDao {

    /** 观察指定周期类型的报告，按期键倒序。 */
    @Query("SELECT * FROM reports WHERE report_type = :type ORDER BY period_key DESC")
    fun observeByType(type: String): Flow<List<ReportEntity>>

    /** 按周期类型 + 期键一次性查询报告；不存在返回 `null`。 */
    @Query("SELECT * FROM reports WHERE report_type = :type AND period_key = :periodKey LIMIT 1")
    suspend fun getByKey(type: String, periodKey: String): ReportEntity?

    /** 插入或更新报告（冲突判定依赖唯一索引 `(report_type, period_key)`）。 */
    @Upsert
    suspend fun upsert(report: ReportEntity)
}
