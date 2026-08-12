package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Semester
import kotlinx.coroutines.flow.Flow

/** 学期仓储接口，由 :data 模块实现。 */
interface SemesterRepository {

    /** 保存学期（新增或更新），返回其主键。 */
    suspend fun save(semester: Semester): Long

    /** 观察全部学期，按起始日期倒序。 */
    fun observeAll(): Flow<List<Semester>>
}
