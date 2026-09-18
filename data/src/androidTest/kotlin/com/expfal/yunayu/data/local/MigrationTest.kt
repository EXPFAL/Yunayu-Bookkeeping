package com.expfal.yunayu.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v1 → v2 与 v2 → v3 迁移测试（androidTest）。
 *
 * 注意：本测试依赖 Android 框架 SQLite，需在设备/模拟器上执行；本机无模拟器/设备，
 * 故写好但不在本机运行，接入 CI 后启用。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        YunayuDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesTagsAndRestoresTransactionTagId() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                    "VALUES ('学习', NULL, 0, '📚', 1, 1)",
            )
            execSQL(
                "INSERT INTO transactions (amount_cents, type, note, tag_id, occurred_at, created_at) " +
                    "VALUES (100, 'EXPENSE', NULL, 1, 1, 1)",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 2, true, YunayuDatabase.MIGRATION_1_2)

        db.query("SELECT name, parent_id FROM tags WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("学习", cursor.getString(0))
            assertNull(cursor.getString(1))
        }

        db.query("SELECT tag_id FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_tags_parent_id_name'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }
    }

    @Test
    fun migrate2To3_dropsSemesterTablesAndPreservesTransactionsAndTags() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                    "VALUES ('学习', NULL, 0, '📚', 1, 1)",
            )
            execSQL(
                "INSERT INTO transactions (amount_cents, type, note, tag_id, occurred_at, created_at) " +
                    "VALUES (100, 'EXPENSE', NULL, 1, 1, 1)",
            )
            execSQL(
                "INSERT INTO semesters (name, start_date, end_date, total_budget_cents) " +
                    "VALUES ('2026春', '2026-02-23', '2026-06-28', 10000)",
            )
            execSQL(
                "INSERT INTO date_ranges (semester_id, range_type, start_date, end_date) " +
                    "VALUES (1, 'EXAM_WEEK', '2026-04-20', '2026-04-26')",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 3, true, YunayuDatabase.MIGRATION_2_3)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'semesters'")
            .use { cursor -> assertFalse(cursor.moveToFirst()) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'date_ranges'")
            .use { cursor -> assertFalse(cursor.moveToFirst()) }

        db.query("SELECT amount_cents FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
        }
        db.query("SELECT name FROM tags WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("学习", cursor.getString(0))
        }
    }

    @Test
    fun migrate3To4_createsReportsTableAndUniqueIndex() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                    "VALUES ('学习', NULL, 0, '📚', 1, 1)",
            )
            execSQL(
                "INSERT INTO transactions (amount_cents, type, note, tag_id, occurred_at, created_at) " +
                    "VALUES (100, 'EXPENSE', NULL, 1, 1, 1)",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 4, true, YunayuDatabase.MIGRATION_3_4)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'reports'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_reports_report_type_period_key'",
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }

        db.query("SELECT amount_cents FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
        }
        db.query("SELECT name FROM tags WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("学习", cursor.getString(0))
        }
    }

    @Test
    fun migrate4To5_createsAccountsAndRebuildsTransactionsWithAccountId() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO tags (name, parent_id, sort_order, icon, created_at, updated_at) " +
                    "VALUES ('学习', NULL, 0, '📚', 1, 1)",
            )
            execSQL(
                "INSERT INTO transactions (amount_cents, type, note, tag_id, occurred_at, created_at) " +
                    "VALUES (100, 'EXPENSE', '买书', 1, 1, 1)",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 5, true, YunayuDatabase.MIGRATION_4_5)

        // accounts 表存在且种子 3 行，名称与顺序对齐 AccountPresets.PRESET_NAMES
        val names = mutableListOf<String>()
        db.query("SELECT name FROM accounts ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        assertEquals(listOf("微信", "支付宝", "银行卡"), names)

        // 唯一索引 index_accounts_name 与 transactions 的 index_transactions_account_id 均存在
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_accounts_name'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_transactions_account_id'",
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }

        // transactions 数据完整，且历史交易 account_id 恒为 NULL
        db.query("SELECT amount_cents, tag_id, account_id FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun migrate5To6_addsInitialBalanceAndCreatesTransfersTable() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO accounts (name, created_at) VALUES ('微信', 1)")
            execSQL("INSERT INTO accounts (name, created_at) VALUES ('支付宝', 2)")
            execSQL(
                "INSERT INTO transactions (amount_cents, type, note, tag_id, account_id, occurred_at, created_at) " +
                    "VALUES (100, 'EXPENSE', '买书', NULL, 1, 1, 1)",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 6, true, YunayuDatabase.MIGRATION_5_6)

        // accounts 既有数据完整，且 initial_balance_cents 默认 0
        val names = mutableListOf<String>()
        db.query("SELECT name, initial_balance_cents FROM accounts ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0)
                assertEquals(0L, cursor.getLong(1))
            }
        }
        assertEquals(listOf("微信", "支付宝"), names)

        // transactions 既有数据完整（account_id 保持既有归属）
        db.query("SELECT amount_cents, account_id FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
        }

        // transfers 表存在
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'transfers'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }

        // transfers 三索引均存在
        listOf(
            "index_transfers_occurred_at",
            "index_transfers_from_account_id",
            "index_transfers_to_account_id",
        ).forEach { indexName ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$indexName'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
    }

    @Test
    fun migrate6To7_addsLocalInsightsColumn() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO reports (" +
                    "report_type, period_key, window_start_ms, window_end_ms, " +
                    "income_cents, expense_cents, top_categories, prev_income_cents, prev_expense_cents, " +
                    "analysis_text, status, engine, content_version, generated_at) " +
                    "VALUES ('MONTHLY', '2026-07', 0, 1, 0, 0, '', 0, 0, NULL, 'SUCCESS', 'api', '1', 1)",
            )
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(TEST_DB, 7, true, YunayuDatabase.MIGRATION_6_7)

        db.query("SELECT local_insights FROM reports WHERE period_key = '2026-07'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
    }

    @Test
    fun migrate7To10_createsSubscriptionsTable() {
        helper.createDatabase(TEST_DB, 7).apply {
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(
                TEST_DB,
                10,
                true,
                YunayuDatabase.MIGRATION_7_10,
            )

        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'subscriptions'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }
    }

    @Test
    fun migrate9To10_addsLocalInsightsPreservingSubscriptions() {
        // 模拟手机 v9：基于 schema v6（无 local_insights）+ subscriptions，user_version=9
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO reports (" +
                    "report_type, period_key, window_start_ms, window_end_ms, " +
                    "income_cents, expense_cents, top_categories, prev_income_cents, prev_expense_cents, " +
                    "analysis_text, status, engine, content_version, generated_at) " +
                    "VALUES ('MONTHLY', '2026-08', 0, 1, 0, 0, '', 0, 0, NULL, 'SUCCESS', 'api', '1', 1)",
            )
            execSQL(
                "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`amount_cents` INTEGER NOT NULL, " +
                    "`billing_cycle` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`is_active` INTEGER NOT NULL DEFAULT 1, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "`billing_start_at` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_posted_at` INTEGER, " +
                    "`last_posted_due_at` INTEGER)",
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `index_subscriptions_is_active` " +
                    "ON `subscriptions` (`is_active`)",
            )
            execSQL(
                "INSERT INTO subscriptions " +
                    "(name, amount_cents, billing_cycle, note, is_active, created_at, updated_at, " +
                    "billing_start_at, last_posted_at, last_posted_due_at) " +
                    "VALUES ('Cursor Pro', 13900, 'MONTHLY', NULL, 1, 1, 1, 0, NULL, NULL)",
            )
            version = 9
            close()
        }

        val db: SupportSQLiteDatabase =
            helper.runMigrationsAndValidate(
                TEST_DB,
                10,
                true,
                YunayuDatabase.MIGRATION_9_10,
            )

        db.query("SELECT local_insights FROM reports WHERE period_key = '2026-08'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        db.query("SELECT name FROM subscriptions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Cursor Pro", cursor.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
