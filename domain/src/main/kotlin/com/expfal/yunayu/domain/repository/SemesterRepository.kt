package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Semester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** 学期仓储接口，由 :data 模块实现。 */
interface SemesterRepository {

    /**
     * 保存学期（新增或更新），返回其主键。
     *
     * 契约：
     * ① 传入已删除学期（`id` 非零但目标行不存在）会抛出 [IllegalStateException]。
     * ② 区间采用先删（仅已知类型 EXAM_WEEK / VACATION）后重写的策略，
     *    `date_ranges.id` 不稳定，调用方不得依赖区间主键。
     * ③ 删除学期会级联删除其区间。
     */
    suspend fun save(semester: Semester): Long

    /**
     * 观察全部学期，按起始日期倒序。
     *
     * 任何学期或区间变更都会重新发射，收集方可按需 [kotlinx.coroutines.flow.distinctUntilChanged]
     * 去重。
     */
    fun observeAll(): Flow<List<Semester>>

    /** 观察指定学期，不存在时发射 null。 */
    fun observeById(id: Long): Flow<Semester?>

    /**
     * 观察当前学期：`today` 落在 `[startDate, endDate]` 区间内（含端点）的学期。
     *
     * 多个学期同时 active 时取 `startDate` 最晚者（假设）；无 active 学期时发射 null。
     */
    fun observeActiveSemester(todayEpochMillis: Long): Flow<Semester?> =
        observeAll().map { semesters ->
            val today = Instant.ofEpochMilli(todayEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            semesters
                .filter { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
                .maxByOrNull { it.startDate }
        }
}
