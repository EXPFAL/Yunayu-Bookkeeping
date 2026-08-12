package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.repository.SemesterRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SemesterRepository] 的 Room 实现。 */
@Singleton
class SemesterRepositoryImpl @Inject constructor(
    private val semesterDao: SemesterDao,
) : SemesterRepository {

    override suspend fun save(semester: Semester): Long =
        semesterDao.insert(semester.toEntity())

    override fun observeAll(): Flow<List<Semester>> =
        semesterDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    private fun Semester.toEntity(): SemesterEntity = SemesterEntity(
        id = id,
        name = name,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        totalBudgetCents = totalBudgetCents,
    )

    private fun SemesterEntity.toDomain(): Semester = Semester(
        id = id,
        name = name,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        totalBudgetCents = totalBudgetCents,
    )
}
