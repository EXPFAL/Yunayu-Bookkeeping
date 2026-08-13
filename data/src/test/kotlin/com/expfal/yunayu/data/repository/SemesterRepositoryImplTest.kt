package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.dao.SemesterDateRangeDao
import com.expfal.yunayu.data.local.entity.SemesterDateRangeEntity
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.domain.model.DateRange
import com.expfal.yunayu.domain.model.Semester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** [SemesterRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class SemesterRepositoryImplTest {

    @Test
    fun `save inserts new semester and rewrites known range types`() = runTest {
        val semesterDao = FakeSemesterDao()
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        val id = repository.save(
            semester(
                examWeeks = listOf(DateRange(LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 26))),
                vacations = listOf(DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5))),
            ),
        )

        assertEquals(1L, id)
        assertTrue(semesterDao.updated.isEmpty())
        val inserted = semesterDao.inserted.single()
        assertEquals(0L, inserted.id)
        assertEquals("2026春", inserted.name)
        assertEquals("2026-02-23", inserted.startDate)
        assertEquals("2026-06-28", inserted.endDate)
        assertEquals(10_000L, inserted.totalBudgetCents)

        val deleted = dateRangeDao.deletedCalls.single()
        assertEquals(1L, deleted.first)
        assertEquals(SemesterDateRangeEntity.KNOWN_RANGE_TYPES, deleted.second)

        val ranges = dateRangeDao.insertedRanges.single()
        assertEquals(2, ranges.size)
        val exam = ranges.first { it.rangeType == SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK }
        assertEquals(1L, exam.semesterId)
        assertEquals("2026-04-20", exam.startDate)
        assertEquals("2026-04-26", exam.endDate)
        val vacation = ranges.first { it.rangeType == SemesterDateRangeEntity.RANGE_TYPE_VACATION }
        assertEquals(1L, vacation.semesterId)
        assertEquals("2026-05-01", vacation.startDate)
        assertEquals("2026-05-05", vacation.endDate)
    }

    @Test
    fun `save updates existing semester when update affects one row`() = runTest {
        val semesterDao = FakeSemesterDao().apply { updateResult = 1 }
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        val id = repository.save(semester(id = 42L, name = "更新后"))

        assertEquals(42L, id)
        assertTrue(semesterDao.inserted.isEmpty())
        val updated = semesterDao.updated.single()
        assertEquals(42L, updated.id)
        assertEquals("更新后", updated.name)
        assertEquals(42L, dateRangeDao.deletedCalls.single().first)
    }

    @Test
    fun `save throws when updating stale id`() = runTest {
        val semesterDao = FakeSemesterDao().apply { updateResult = 0 }
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        val error = runCatching { repository.save(semester(id = 99L)) }.exceptionOrNull()
        require(error is IllegalStateException) { "expected IllegalStateException but got $error" }

        assertTrue(error.message.orEmpty().contains("99"))
        assertTrue(dateRangeDao.insertedRanges.isEmpty())
        assertTrue(dateRangeDao.deletedCalls.isEmpty())
    }

    @Test
    fun `save deletes only known range types before rewriting`() = runTest {
        val semesterDao = FakeSemesterDao()
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        repository.save(semester(id = 5L))

        val deleted = dateRangeDao.deletedCalls.single()
        assertEquals(5L, deleted.first)
        assertEquals(
            listOf(
                SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK,
                SemesterDateRangeEntity.RANGE_TYPE_VACATION,
            ),
            deleted.second,
        )
    }

    @Test
    fun `observeAll groups exam week and vacation ranges`() = runTest {
        val semesterDao = FakeSemesterDao()
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        semesterDao.observeAllFlow = flowOf(
            listOf(
                SemesterEntity(
                    id = 7L,
                    name = "2026春",
                    startDate = "2026-02-23",
                    endDate = "2026-06-28",
                    totalBudgetCents = 20_000L,
                ),
            ),
        )
        dateRangeDao.observeBySemesterFlows[7L] = flowOf(
            listOf(
                range(7L, SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK, "2026-04-20", "2026-04-26"),
                range(7L, SemesterDateRangeEntity.RANGE_TYPE_VACATION, "2026-05-01", "2026-05-05"),
            ),
        )

        val result = repository.observeAll().first()

        val semester = result.single()
        assertEquals(7L, semester.id)
        assertEquals(20_000L, semester.totalBudgetCents)
        assertEquals(
            listOf(DateRange(LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 26))),
            semester.examWeekRanges,
        )
        assertEquals(
            listOf(DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5))),
            semester.vacationRanges,
        )
    }

    @Test
    fun `observeAll skips invalid date records without crashing`() = runTest {
        val semesterDao = FakeSemesterDao()
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        semesterDao.observeAllFlow = flowOf(
            listOf(
                SemesterEntity(1L, "正常", "2026-02-23", "2026-06-28", 100L),
                SemesterEntity(2L, "坏日期", "not-a-date", "2026-06-28", 100L),
            ),
        )
        dateRangeDao.observeBySemesterFlows[1L] = flowOf(
            listOf(range(1L, SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK, "2026-04-20", "bad")),
        )

        val result = repository.observeAll().first()

        assertEquals(1, result.size)
        val semester = result.single()
        assertEquals(1L, semester.id)
        assertTrue(semester.examWeekRanges.isEmpty())
    }

    @Test
    fun `observeAll filters unknown range type`() = runTest {
        val semesterDao = FakeSemesterDao()
        val dateRangeDao = FakeSemesterDateRangeDao()
        val repository = repository(semesterDao, dateRangeDao)

        semesterDao.observeAllFlow = flowOf(
            listOf(
                SemesterEntity(1L, "2026春", "2026-02-23", "2026-06-28", 100L),
            ),
        )
        dateRangeDao.observeBySemesterFlows[1L] = flowOf(
            listOf(
                range(1L, SemesterDateRangeEntity.RANGE_TYPE_EXAM_WEEK, "2026-04-20", "2026-04-26"),
                range(1L, "UNKNOWN", "2026-05-01", "2026-05-02"),
            ),
        )

        val result = repository.observeAll().first()

        val semester = result.single()
        assertEquals(1, semester.examWeekRanges.size)
        assertTrue(semester.vacationRanges.isEmpty())
    }

    private fun repository(semesterDao: SemesterDao, dateRangeDao: SemesterDateRangeDao) =
        SemesterRepositoryImpl(
            database = FakeYunayuDatabase(semesterDao, dateRangeDao),
            semesterDao = semesterDao,
            dateRangeDao = dateRangeDao,
        )

    private fun semester(
        id: Long = 0L,
        name: String = "2026春",
        start: LocalDate = LocalDate.of(2026, 2, 23),
        end: LocalDate = LocalDate.of(2026, 6, 28),
        budget: Long = 10_000L,
        examWeeks: List<DateRange> = emptyList(),
        vacations: List<DateRange> = emptyList(),
    ) = Semester(
        id = id,
        name = name,
        startDate = start,
        endDate = end,
        totalBudgetCents = budget,
        examWeekRanges = examWeeks,
        vacationRanges = vacations,
    )

    private fun range(semesterId: Long, rangeType: String, start: String, end: String) =
        SemesterDateRangeEntity(
            semesterId = semesterId,
            rangeType = rangeType,
            startDate = start,
            endDate = end,
        )
}
