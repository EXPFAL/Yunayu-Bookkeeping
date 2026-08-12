package com.expfal.yunayu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expfal.yunayu.data.local.dao.SemesterDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.SemesterEntity
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity

/**
 * Yunayu 数据库。包含 tags / transactions / semesters 三张表。
 *
 * Schema 变更策略：version 递增 + 显式 Migration，禁止 fallbackToDestructiveMigration
 * （用户数据不可丢失）；schema 经 exportSchema 输出至 data/schemas。
 */
@Database(
    entities = [
        TagEntity::class,
        TransactionEntity::class,
        SemesterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class YunayuDatabase : RoomDatabase() {

    abstract fun tagDao(): TagDao

    abstract fun transactionDao(): TransactionDao

    abstract fun semesterDao(): SemesterDao

    companion object {
        const val NAME = "yunayu.db"

        /** PRD P0-3 内置四大类根标签（学习/社交/生活/娱乐）。 */
        private val ROOT_TAGS = listOf(
            "学习" to "📚",
            "社交" to "🤝",
            "生活" to "🏠",
            "娱乐" to "🎮",
        )

        /** 首次建库种子化四大类根节点（SCAFFOLD.md 4.4）。 */
        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                ROOT_TAGS.forEachIndexed { index, (name, icon) ->
                    db.execSQL(
                        "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                            "VALUES (?, NULL, ?, ?, ?, ?)",
                        arrayOf(name, index, icon, now, now),
                    )
                }
            }
        }
    }
}
