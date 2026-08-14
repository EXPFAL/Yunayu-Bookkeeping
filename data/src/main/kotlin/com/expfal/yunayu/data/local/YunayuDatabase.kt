package com.expfal.yunayu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity

/**
 * Yunayu 数据库。包含 tags / transactions 两张表（月度预算经 DataStore 存储，不落库）。
 *
 * Schema 变更策略：version 递增 + 显式 Migration，禁止 fallbackToDestructiveMigration
 * （用户数据不可丢失）；schema 经 exportSchema 输出至 data/schemas。
 */
@Database(
    entities = [
        TagEntity::class,
        TransactionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class YunayuDatabase : RoomDatabase() {

    abstract fun tagDao(): TagDao

    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "yunayu.db"

        /** PRD P0-3 内置四大类根标签（学习/社交/生活/娱乐）。 */
        private val ROOT_TAGS = listOf(
            "学习" to "📚",
            "社交" to "🤝",
            "生活" to "🏠",
            "娱乐" to "🎮",
        )

        /**
         * Schema v1 → v2 迁移（见 SCAFFOLD.md「Schema v2 增强记录」）。
         *
         * 1. tags 表重建：新增自引用外键 parent_id → id (CASCADE) 与唯一索引 (parent_id, name)。
         * 2. transactions 新增复合索引 (occurred_at, type)。
         * 3. 新建 date_ranges 子表（考试周 / 假期区间）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room 2.6.1 不会自动为 migration 包事务：任何一条语句中断都会留下
                // 半迁移状态，导致下次启动在 version=2 与缺失对象之间反复崩溃。故整体包事务。
                db.beginTransaction()
                try {
                    // 1) tags 表重建：自引用外键 + 唯一索引（保留四大类根节点及其子树）
                    // 外键指向临时名 tags_new，RENAME 后由 SQLite 改写为 tags
                    db.execSQL(
                        "CREATE TABLE `tags_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`parent_id` INTEGER, " +
                            "`sort_order` INTEGER NOT NULL, " +
                            "`icon` TEXT, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`parent_id`) REFERENCES `tags_new`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL(
                        "INSERT INTO `tags_new` " +
                            "(`id`, `name`, `parent_id`, `sort_order`, `icon`, `created_at`, `updated_at`) " +
                            "SELECT `id`, `name`, `parent_id`, `sort_order`, `icon`, `created_at`, `updated_at` " +
                            "FROM `tags`",
                    )
                    // 备份 transactions.tag_id：DROP 旧 tags 会触发既有外键 SET NULL，需重建后恢复
                    db.execSQL(
                        "CREATE TABLE `_tx_tag_backup` " +
                            "(`id` INTEGER PRIMARY KEY NOT NULL, `tag_id` INTEGER)",
                    )
                    db.execSQL(
                        "INSERT INTO `_tx_tag_backup` (`id`, `tag_id`) " +
                            "SELECT `id`, `tag_id` FROM `transactions` WHERE `tag_id` IS NOT NULL",
                    )
                    db.execSQL("DROP TABLE `tags`")
                    db.execSQL("ALTER TABLE `tags_new` RENAME TO `tags`")
                    db.execSQL(
                        "UPDATE `transactions` SET `tag_id` = (" +
                            "SELECT `_tx_tag_backup`.`tag_id` FROM `_tx_tag_backup` " +
                            "WHERE `_tx_tag_backup`.`id` = `transactions`.`id`" +
                            ") WHERE `id` IN (SELECT `id` FROM `_tx_tag_backup`)",
                    )
                    db.execSQL("DROP TABLE `_tx_tag_backup`")
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_parent_id_name` " +
                            "ON `tags` (`parent_id`, `name`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_tags_parent_id_sort_order` " +
                            "ON `tags` (`parent_id`, `sort_order`)",
                    )

                    // 2) transactions 新增复合索引 (occurred_at, type)
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at_type` " +
                            "ON `transactions` (`occurred_at`, `type`)",
                    )

                    // 3) 新建 date_ranges 子表（考试周 / 假期区间）
                    db.execSQL(
                        "CREATE TABLE `date_ranges` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`semester_id` INTEGER NOT NULL, " +
                            "`range_type` TEXT NOT NULL, " +
                            "`start_date` TEXT NOT NULL, " +
                            "`end_date` TEXT NOT NULL, " +
                            "FOREIGN KEY(`semester_id`) REFERENCES `semesters`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_date_ranges_semester_id` " +
                            "ON `date_ranges` (`semester_id`)",
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        /**
         * Schema v2 → v3 迁移：删除学期两表（semesters / date_ranges）。
         *
         * 学期预算已改为月度预算，semesters 与 date_ranges 不再使用；tags / transactions
         * 数据保留。先 DROP 子表 date_ranges（含指向 semesters 的外键），再 DROP 父表 semesters，
         * 避免外键强制开启时因引用顺序导致约束失败；整体包事务，防止半迁移状态。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    db.execSQL("DROP TABLE `date_ranges`")
                    db.execSQL("DROP TABLE `semesters`")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

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
