package com.expfal.yunayu.data.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.dao.SemesterDateRangeDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.SemesterDateRangeEntity
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.Executor

/** [SemesterDao] 手写 fake：记录写入与读取调用，返回值可由测试控制。 */
class FakeSemesterDao : SemesterDao {

    val inserted = mutableListOf<SemesterEntity>()
    val updated = mutableListOf<SemesterEntity>()
    var updateResult: Int = 1
    var nextInsertId: Long = 1L
    var observeAllFlow: Flow<List<SemesterEntity>> = flowOf(emptyList())

    override suspend fun insert(semester: SemesterEntity): Long {
        inserted += semester
        return nextInsertId
    }

    override suspend fun update(semester: SemesterEntity): Int {
        updated += semester
        return updateResult
    }

    override fun observeAll(): Flow<List<SemesterEntity>> = observeAllFlow
}

/** [SemesterDateRangeDao] 手写 fake：记录删除/写入调用，供断言区间重写语义。 */
class FakeSemesterDateRangeDao : SemesterDateRangeDao {

    val deletedCalls = mutableListOf<Pair<Long, List<String>>>()
    val insertedRanges = mutableListOf<List<SemesterDateRangeEntity>>()
    val observeBySemesterFlows = mutableMapOf<Long, Flow<List<SemesterDateRangeEntity>>>()

    override fun observeBySemester(semesterId: Long): Flow<List<SemesterDateRangeEntity>> =
        observeBySemesterFlows[semesterId] ?: flowOf(emptyList())

    override suspend fun insertAll(ranges: List<SemesterDateRangeEntity>) {
        insertedRanges += ranges
    }

    override suspend fun deleteBySemesterIdAndRangeTypes(semesterId: Long, rangeTypes: List<String>) {
        deletedCalls += semesterId to rangeTypes
    }
}

/** [TagDao] 手写 fake：按 parentId 返回预置子节点。 */
class FakeTagDao : TagDao {

    var childrenByParent: Map<Long?, List<TagEntity>> = emptyMap()

    override fun observeChildren(parentId: Long?): Flow<List<TagEntity>> =
        flowOf(childrenByParent[parentId] ?: emptyList())

    override suspend fun getChildren(parentId: Long?): List<TagEntity> =
        childrenByParent[parentId] ?: emptyList()

    override suspend fun updateSortOrder(tags: List<TagEntity>) = Unit

    override suspend fun insert(tag: TagEntity): Long = 0L

    override suspend fun insertAll(tags: List<TagEntity>): List<Long> = emptyList()
}

/**
 * [YunayuDatabase] 手写 fake：仅用于让 [SemesterRepositoryImpl.save] 的
 * `withTransaction` 可在 JVM 单测中执行——事务执行器改为直连执行，事务边界方法改为 no-op，
 * 未使用的 DAO/OpenHelper 一律抛异常以暴露误用。
 *
 * RoomDatabase 构造函数会调用 createInvalidationTracker() 初始化追踪器，
 * 故返回真实 InvalidationTracker（本单测不使用）。
 */
@Suppress("OVERRIDE_DEPRECATION")
class FakeYunayuDatabase(
    private val semesterDaoImpl: SemesterDao,
    private val dateRangeDaoImpl: SemesterDateRangeDao,
) : YunayuDatabase() {

    private val directExecutor = Executor { command -> command.run() }

    init {
        // RoomDatabase.getTransactionExecutor() 是 final（返回 private lateinit 字段
        // internalTransactionExecutor，仅由 init(configuration) 赋值），无法 override。
        // save() 的 withTransaction 必须经该执行器调度事务块，故反射注入直连执行器，
        // 使 withTransaction 在本 JVM 单测中同步执行（事务边界方法已覆盖为 no-op）。
        val field = RoomDatabase::class.java.getDeclaredField("internalTransactionExecutor")
        field.isAccessible = true
        field.set(this, directExecutor)
    }

    override fun semesterDao(): SemesterDao = semesterDaoImpl

    override fun semesterDateRangeDao(): SemesterDateRangeDao = dateRangeDaoImpl

    override fun tagDao(): TagDao = throw UnsupportedOperationException("Not used in unit tests")

    override fun transactionDao(): TransactionDao =
        throw UnsupportedOperationException("Not used in unit tests")

    override fun clearAllTables(): Unit =
        throw UnsupportedOperationException("Not used in unit tests")

    override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

    override fun createOpenHelper(configuration: DatabaseConfiguration): SupportSQLiteOpenHelper =
        throw UnsupportedOperationException("Not used in unit tests")

    override fun beginTransaction(): Unit = Unit

    override fun setTransactionSuccessful(): Unit = Unit

    override fun endTransaction(): Unit = Unit
}
