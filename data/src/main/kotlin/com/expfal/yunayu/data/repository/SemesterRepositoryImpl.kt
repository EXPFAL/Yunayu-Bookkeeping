package com.expfal.yunayu.data.repository

import android.util.Log
import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.repository.SemesterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** [SemesterRepository] 的 Room 实现。 */
@Singleton
class SemesterRepositoryImpl @Inject constructor(
    private val semesterDao: SemesterDao,
) : SemesterRepository {

    override suspend fun save(semester: Semester): Long =
        semesterDao.insert(semester.toEntity())

    override fun observeAll(): Flow<List<Semester>> =
        semesterDao.observeAll().map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    private fun Semester.toEntity(): SemesterEntity = SemesterEntity(
        id = id,
        name = name,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        totalBudgetCents = totalBudgetCents,
    )

    private fun SemesterEntity.toDomainOrNull(): Semester? {
        val startDate = parseDateOrNull(this.startDate, "startDate") ?: return null
        val endDate = parseDateOrNull(this.endDate, "endDate") ?: return null
        return Semester(
            id = id,
            name = name,
            startDate = startDate,
            endDate = endDate,
            totalBudgetCents = totalBudgetCents,
        )
    }

    private fun SemesterEntity.parseDateOrNull(raw: String, field: String): LocalDate? =
        runCatching { LocalDate.parse(raw) }
            .onFailure { Log.w(TAG, "Invalid $field \"$raw\" for semester id=$id, record skipped") }
            .getOrNull()

    private companion object {
        const val TAG = "SemesterRepo"
    }
}
