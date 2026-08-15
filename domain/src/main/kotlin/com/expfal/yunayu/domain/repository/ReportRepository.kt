package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import kotlinx.coroutines.flow.Flow

/** 报告仓储接口，由 :data 模块实现。 */
interface ReportRepository {

    /** 观察指定周期类型的报告列表，按期键倒序。 */
    fun observeByType(type: ReportPeriodType): Flow<List<Report>>

    /** 按周期类型 + 期键一次性查询报告；不存在返回 `null`。 */
    suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report?

    /** 插入或更新一份报告。 */
    suspend fun upsert(report: Report)
}
