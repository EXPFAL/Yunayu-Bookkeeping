package com.expfal.yunayu.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v1 → v2 迁移测试（androidTest）。
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
