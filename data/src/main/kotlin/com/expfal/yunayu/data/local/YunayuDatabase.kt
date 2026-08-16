package com.expfal.yunayu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.ReportDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.data.local.entity.ReportEntity
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.domain.model.AccountPresets

/**
 * Yunayu 数据库。包含 accounts / tags / transactions / reports 四张表（月度预算经 DataStore 存储，不落库）。
 *
 * Schema 变更策略：version 递增 + 显式 Migration，禁止 fallbackToDestructiveMigration
 * （用户数据不可丢失）；schema 经 exportSchema 输出至 data/schemas。
 */
@Database(
    entities = [
        AccountEntity::class,
        TagEntity::class,
        TransactionEntity::class,
        ReportEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class YunayuDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun tagDao(): TagDao

    abstract fun transactionDao(): TransactionDao

    abstract fun reportDao(): ReportDao

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
         * 种子子标签：根类名 → 子标签名列表（列表顺序即 sortOrder 递增）。
         *
         * v2 来源：用户 2026-06~08 三个月真实账单（307 笔）的消费分类结构归纳，2026-08 决策。
         * v3 扩充：基于半年账单分析（832 条）追加 4 个子标签——生活「洗衣/水果/医疗」、
         * 娱乐「骑行」（与「运动」并存），2026-08 决策。
         * 仅首次建库（onCreate）执行一次，存量库需卸载重装（或经标签管理界面手工添加）才会生效。
         */
        private val SEED_SUB_TAGS = mapOf(
            "学习" to listOf("课本教辅", "考证", "实习", "订阅"),
            "社交" to listOf("聚餐"),
            "生活" to listOf("餐饮", "饮品", "交通", "购物", "生活缴费", "洗衣", "水果", "医疗"),
            "娱乐" to listOf("游戏", "运动", "出游", "骑行"),
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
                            "FOREIGN KEY(`parent_id`) REFERENCES `tags`(`id`) " +
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

        /**
         * Schema v3 → v4 迁移：新增 reports 报告表 + 唯一索引 (report_type, period_key)。
         *
         * SQL 与 Room 由 [ReportEntity] 生成的 schema 严格一致（列名 / 类型 / 非空约束 / 列顺序），
         * 整体包事务，防止半迁移状态。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `reports` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`report_type` TEXT NOT NULL, " +
                            "`period_key` TEXT NOT NULL, " +
                            "`window_start_ms` INTEGER NOT NULL, " +
                            "`window_end_ms` INTEGER NOT NULL, " +
                            "`income_cents` INTEGER NOT NULL, " +
                            "`expense_cents` INTEGER NOT NULL, " +
                            "`top_categories` TEXT NOT NULL, " +
                            "`prev_income_cents` INTEGER NOT NULL, " +
                            "`prev_expense_cents` INTEGER NOT NULL, " +
                            "`analysis_text` TEXT, " +
                            "`status` TEXT NOT NULL, " +
                            "`engine` TEXT NOT NULL, " +
                            "`content_version` TEXT NOT NULL, " +
                            "`generated_at` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_reports_report_type_period_key` " +
                            "ON `reports` (`report_type`, `period_key`)",
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        /**
         * Schema v4 → v5 迁移：新增 accounts 账户表 + 种子预置账户，transactions 表重建以新增
         * account_id 外键列。
         *
         * 1. 新建 accounts 表 + 唯一索引 (name)。
         * 2. 种子预置账户（单一数据源 [AccountPresets.PRESET_NAMES]）。
         * 3. transactions 表重建（8 列：原 7 列 + account_id 置于 tag_id 之后），历史数据
         *    account_id 恒为 NULL（历史不导入账户归属）。
         *
         * SQL 与 Room 由 [AccountEntity] / [TransactionEntity] 生成的 schema 严格一致（列名 / 类型 /
         * 非空约束 / 列顺序 / 外键 / 索引），整体包事务，防止半迁移状态。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room 2.6.1 不会自动为 migration 包事务：任何一条语句中断都会留下半迁移状态。
                db.beginTransaction()
                try {
                    // 1) 新建 accounts 表 + 唯一索引 (name)
                    db.execSQL(
                        "CREATE TABLE `accounts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)",
                    )

                    // 2) 种子预置账户（单一数据源 AccountPresets.PRESET_NAMES）
                    val now = System.currentTimeMillis()
                    val seedSql = buildString {
                        append("INSERT OR IGNORE INTO accounts(name, created_at) VALUES ")
                        append(AccountPresets.PRESET_NAMES.joinToString(", ") { "('$it', ?)" })
                    }
                    db.execSQL(seedSql, Array(AccountPresets.PRESET_NAMES.size) { now as Any? })

                    // 3) transactions 表重建：新增 account_id 列（置于 tag_id 之后），
                    // 外键写最终表名（tags / accounts），RENAME 后引用保持有效
                    db.execSQL(
                        "CREATE TABLE `transactions_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`amount_cents` INTEGER NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`note` TEXT, " +
                            "`tag_id` INTEGER, " +
                            "`account_id` INTEGER, " +
                            "`occurred_at` INTEGER NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE SET NULL, " +
                            "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE SET NULL )",
                    )
                    // 历史数据 account_id 恒 NULL（历史不导入账户归属），无需 1_2 的备份表环节：
                    // 此处 DROP 的是子表 transactions（非被其它表外键引用的父表），不会触发其它表
                    // 对本表的 SET NULL，故直接重建即可。
                    db.execSQL(
                        "INSERT INTO `transactions_new` " +
                            "(`id`, `amount_cents`, `type`, `note`, `tag_id`, `account_id`, `occurred_at`, `created_at`) " +
                            "SELECT `id`, `amount_cents`, `type`, `note`, `tag_id`, NULL, `occurred_at`, `created_at` " +
                            "FROM `transactions`",
                    )
                    db.execSQL("DROP TABLE `transactions`")
                    db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

                    // 4) 重建索引（DROP transactions 会连带删除其上的索引）
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_tag_id` ON `transactions` (`tag_id`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at` ON `transactions` (`occurred_at`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at_type` ON `transactions` (`occurred_at`, `type`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_account_id` ON `transactions` (`account_id`)",
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        /**
         * 首次建库种子化四大类根节点及其子标签（SCAFFOLD.md 4.4 / §12）。
         *
         * 子标签来源：2026-06~08 三个月账单（307 笔）+ 半年账单分析（832 条）扩充（2026-08 决策）。
         * 本回调仅在 onCreate 首次建库事务内执行一次，存量库需卸载重装才会生效。
         */
        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                ROOT_TAGS.forEachIndexed { index, (name, icon) ->
                    db.execSQL(
                        "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                            "VALUES (?, NULL, ?, ?, ?, ?)",
                        arrayOf<Any?>(name, index, icon, now, now),
                    )
                }
                ROOT_TAGS.forEach { (rootName, _) ->
                    val rootId = queryRootId(db, rootName)
                    SEED_SUB_TAGS[rootName].orEmpty().forEachIndexed { index, subName ->
                        db.execSQL(
                            "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(subName, rootId, index, null, now, now),
                        )
                    }
                }
            }
        }

        /** 按名称回查根节点 id（不硬编码自增主键，兼容未来根类调整）。 */
        private fun queryRootId(db: SupportSQLiteDatabase, name: String): Long =
            db.query("SELECT id FROM tags WHERE name = ? AND parent_id IS NULL", arrayOf(name))
                .use { cursor ->
                    check(cursor.moveToFirst()) { "种子根标签缺失: $name" }
                    cursor.getLong(0)
                }
    }
}
