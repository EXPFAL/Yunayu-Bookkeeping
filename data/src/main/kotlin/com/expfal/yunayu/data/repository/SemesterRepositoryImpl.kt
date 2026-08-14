package com.expfal.yunayu.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.dao.SemesterDateRangeDao
import com.expfal.yunayu.data.local.entity.SemesterDateRangeEntity
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.domain.model.DateRange
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.repository.SemesterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** [SemesterRepository] 的 Room 实现。 */
@Singleton
class SemesterRepositoryImpl @Inject constructor(
    private val database: YunayuDatabase,
    private val semesterDao: SemesterDao,
    private val dateRangeDao: SemesterDateRangeDao,
) : SemesterRepository {

    override suspend fun save(semester: Semester): Long = database.withTransaction {
        val semesterId = if (semester.id == 0L) {
            semesterDao.insert(semester.toEntity())
        } else {
            val updated = semesterDao.update(semester.toEntity())
            check(updated == 1) { "Semester id=${semester.id} not found" }
            semester.id
        }
        dateRangeDao.deleteBySemesterIdAndRangeTypes(
            semesterId,
            SemesterDateRangeEntity.KNOWN_RANGE_TYPES,
        )
        dateRangeDao.insertAll(semester.toRangeEntities(semesterId))
        semesterId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<Semester>> =
        semesterDao.observeAll().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    entities.map { entity ->
                        dateRangeDao.observeBySemester(entity.id)
                            .map { ranges -> entity.toDomain(ranges) }
                    },
                ) { semesters -> semesters.filterNotNull() }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeById(id: Long): Flow<Semester?> =
        semesterDao.observeById(id).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                dateRangeDao.observeBySemester(id).map { ranges -> entity.toDomain(ranges) }
            }
        }

    private fun Semester.toEntity(): SemesterEntity = SemesterEntity(
        id = id,
        name = name,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        totalBudgetCents = totalBudgetCents,
    )

    private fun Semester.toRangeEntities(semesterId: Long): List<SemesterDateRangeEntity> =
        examWeekRanges.map { it.toEntity(semesterId, SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK) } +
            vacationRanges.map { it.toEntity(semesterId, SemesterDateRangeEntity.RANGE_TYPE_VACATION) }

    private fun DateRange.toEntity(semesterId: Long, rangeType: String): SemesterDateRangeEntity =
        SemesterDateRangeEntity(
            semesterId = semesterId,
            rangeType = rangeType,
            startDate = start.toString(),
            endDate = endInclusive.toString(),
        )

    private fun SemesterEntity.toDomain(ranges: List<SemesterDateRangeEntity>): Semester? {
        val startDate = parseDateOrNull(this.startDate, "startDate") ?: return null
        val endDate = parseDateOrNull(this.endDate, "endDate") ?: return null
        ranges
            .filter { it.rangeType !in SemesterDateRangeEntity.KNOWN_RANGE_TYPES }
            .forEach { Log.w(TAG, "Unknown range_type \"${it.rangeType}\" for date range id=${it.id}, record skipped") }
        val examWeekRanges = ranges
            .filter { it.rangeType == SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK }
            .mapNotNull { it.toDateRangeOrNull() }
        val vacationRanges = ranges
            .filter { it.rangeType == SemesterDateRangeEntity.RANGE_TYPE_VACATION }
            .mapNotNull { it.toDateRangeOrNull() }
        return Semester(
            id = id,
            name = name,
            startDate = startDate,
            endDate = endDate,
            totalBudgetCents = totalBudgetCents,
            examWeekRanges = examWeekRanges,
            vacationRanges = vacationRanges,
        )
    }

    private fun SemesterDateRangeEntity.toDateRangeOrNull(): DateRange? {
        val start = runCatching { LocalDate.parse(startDate) }
            .onFailure { Log.w(TAG, "Invalid startDate \"$startDate\" for date range id=$id, record skipped") }
            .getOrNull() ?: return null
        val end = runCatching { LocalDate.parse(endDate) }
            .onFailure { Log.w(TAG, "Invalid endDate \"$endDate\" for date range id=$id, record skipped") }
            .getOrNull() ?: return null
        return DateRange(start = start, endInclusive = end)
    }

    private fun SemesterEntity.parseDateOrNull(raw: String, field: String): LocalDate? =
        runCatching { LocalDate.parse(raw) }
            .onFailure { Log.w(TAG, "Invalid $field \"$raw\" for semester id=$id, record skipped") }
            .getOrNull()

    private companion object {
        const val TAG = "SemesterRepo"
    }
}
