package com.expfal.yunayu.domain.backup

import com.expfal.yunayu.domain.model.BackupAccount
import com.expfal.yunayu.domain.model.BackupReport
import com.expfal.yunayu.domain.model.BackupSubscription
import com.expfal.yunayu.domain.model.BackupTag
import com.expfal.yunayu.domain.model.BackupTransaction
import com.expfal.yunayu.domain.model.BackupTransfer
import com.expfal.yunayu.domain.model.LedgerBackup

/**
 * 账本备份 JSON 编解码（纯函数，不依赖 Android）。
 *
 * 用手写轻量解析避免 :domain 引入第三方 JSON 库；字段集合固定、无嵌套嵌套数组以外的复杂结构。
 */
object LedgerBackupCodec {

    fun encode(backup: LedgerBackup): String = buildString {
        append('{')
        appendJsonString("format", backup.format)
        append(',')
        appendJsonNumber("formatVersion", backup.formatVersion.toLong())
        append(',')
        appendJsonNumber("dbVersion", backup.dbVersion.toLong())
        append(',')
        appendJsonNumber("exportedAt", backup.exportedAt)
        append(',')
        append("\"accounts\":[")
        backup.accounts.forEachIndexed { i, a ->
            if (i > 0) append(',')
            appendAccount(a)
        }
        append("],\"tags\":[")
        backup.tags.forEachIndexed { i, t ->
            if (i > 0) append(',')
            appendTag(t)
        }
        append("],\"transactions\":[")
        backup.transactions.forEachIndexed { i, t ->
            if (i > 0) append(',')
            appendTransaction(t)
        }
        append("],\"transfers\":[")
        backup.transfers.forEachIndexed { i, t ->
            if (i > 0) append(',')
            appendTransfer(t)
        }
        append("],\"subscriptions\":[")
        backup.subscriptions.forEachIndexed { i, s ->
            if (i > 0) append(',')
            appendSubscription(s)
        }
        append("],\"reports\":[")
        backup.reports.forEachIndexed { i, r ->
            if (i > 0) append(',')
            appendReport(r)
        }
        append("],")
        appendJsonNumber("monthlyBudgetCents", backup.monthlyBudgetCents)
        append(',')
        if (backup.lastUsedAccountId == null) {
            append("\"lastUsedAccountId\":null")
        } else {
            appendJsonNumber("lastUsedAccountId", backup.lastUsedAccountId)
        }
        append(",\"subscriptionReminderKeys\":[")
        backup.subscriptionReminderKeys.toList().forEachIndexed { i, key ->
            if (i > 0) append(',')
            append('"').append(escape(key)).append('"')
        }
        append(']')
        append('}')
    }

    fun decode(json: String): LedgerBackup {
        val root = JsonObjectParser.parseObject(json)
        val format = root.string("format")
            ?: throw IllegalArgumentException("missing format")
        val formatVersion = root.int("formatVersion")
            ?: throw IllegalArgumentException("missing formatVersion")
        val dbVersion = root.int("dbVersion")
            ?: throw IllegalArgumentException("missing dbVersion")
        val exportedAt = root.long("exportedAt")
            ?: throw IllegalArgumentException("missing exportedAt")
        return LedgerBackup(
            format = format,
            formatVersion = formatVersion,
            dbVersion = dbVersion,
            exportedAt = exportedAt,
            accounts = root.array("accounts").map { parseAccount(it.asObject()) },
            tags = root.array("tags").map { parseTag(it.asObject()) },
            transactions = root.array("transactions").map { parseTransaction(it.asObject()) },
            transfers = root.array("transfers").map { parseTransfer(it.asObject()) },
            subscriptions = root.array("subscriptions").map { parseSubscription(it.asObject()) },
            reports = root.array("reports").map { parseReport(it.asObject()) },
            monthlyBudgetCents = root.long("monthlyBudgetCents") ?: 0L,
            lastUsedAccountId = root.longOrNull("lastUsedAccountId"),
            subscriptionReminderKeys = root.array("subscriptionReminderKeys")
                .mapNotNull { it.asStringOrNull() }
                .toSet(),
        )
    }

    private fun StringBuilder.appendAccount(a: BackupAccount) {
        append('{')
        appendJsonNumber("id", a.id)
        append(',')
        appendJsonString("name", a.name)
        append(',')
        appendJsonNumber("createdAt", a.createdAt)
        append(',')
        appendJsonNumber("initialBalanceCents", a.initialBalanceCents)
        append('}')
    }

    private fun StringBuilder.appendTag(t: BackupTag) {
        append('{')
        appendJsonNumber("id", t.id)
        append(',')
        appendJsonString("name", t.name)
        append(',')
        if (t.parentId == null) append("\"parentId\":null") else appendJsonNumber("parentId", t.parentId)
        append(',')
        appendJsonNumber("sortOrder", t.sortOrder.toLong())
        append(',')
        if (t.icon == null) append("\"icon\":null") else appendJsonString("icon", t.icon)
        append(',')
        appendJsonNumber("createdAt", t.createdAt)
        append(',')
        appendJsonNumber("updatedAt", t.updatedAt)
        append('}')
    }

    private fun StringBuilder.appendTransaction(t: BackupTransaction) {
        append('{')
        appendJsonNumber("id", t.id)
        append(',')
        appendJsonNumber("amountCents", t.amountCents)
        append(',')
        appendJsonString("type", t.type)
        append(',')
        if (t.note == null) append("\"note\":null") else appendJsonString("note", t.note)
        append(',')
        if (t.tagId == null) append("\"tagId\":null") else appendJsonNumber("tagId", t.tagId)
        append(',')
        if (t.accountId == null) append("\"accountId\":null") else appendJsonNumber("accountId", t.accountId)
        append(',')
        appendJsonNumber("occurredAt", t.occurredAt)
        append(',')
        appendJsonNumber("createdAt", t.createdAt)
        append('}')
    }

    private fun StringBuilder.appendTransfer(t: BackupTransfer) {
        append('{')
        appendJsonNumber("id", t.id)
        append(',')
        appendJsonNumber("fromAccountId", t.fromAccountId)
        append(',')
        appendJsonNumber("toAccountId", t.toAccountId)
        append(',')
        appendJsonNumber("amountCents", t.amountCents)
        append(',')
        if (t.note == null) append("\"note\":null") else appendJsonString("note", t.note)
        append(',')
        appendJsonNumber("occurredAt", t.occurredAt)
        append(',')
        appendJsonNumber("createdAt", t.createdAt)
        append('}')
    }

    private fun StringBuilder.appendSubscription(s: BackupSubscription) {
        append('{')
        appendJsonNumber("id", s.id)
        append(',')
        appendJsonString("name", s.name)
        append(',')
        appendJsonNumber("amountCents", s.amountCents)
        append(',')
        appendJsonString("billingCycle", s.billingCycle)
        append(',')
        if (s.note == null) append("\"note\":null") else appendJsonString("note", s.note)
        append(',')
        append("\"isActive\":").append(s.isActive)
        append(',')
        appendJsonNumber("billingStartAt", s.billingStartAt)
        append(',')
        if (s.lastPostedDueAt == null) {
            append("\"lastPostedDueAt\":null")
        } else {
            appendJsonNumber("lastPostedDueAt", s.lastPostedDueAt)
        }
        append(',')
        if (s.lastPostedAt == null) {
            append("\"lastPostedAt\":null")
        } else {
            appendJsonNumber("lastPostedAt", s.lastPostedAt)
        }
        append(',')
        appendJsonNumber("createdAt", s.createdAt)
        append(',')
        appendJsonNumber("updatedAt", s.updatedAt)
        append('}')
    }

    private fun StringBuilder.appendReport(r: BackupReport) {
        append('{')
        appendJsonNumber("id", r.id)
        append(',')
        appendJsonString("reportType", r.reportType)
        append(',')
        appendJsonString("periodKey", r.periodKey)
        append(',')
        appendJsonNumber("windowStartMs", r.windowStartMs)
        append(',')
        appendJsonNumber("windowEndMs", r.windowEndMs)
        append(',')
        appendJsonNumber("incomeCents", r.incomeCents)
        append(',')
        appendJsonNumber("expenseCents", r.expenseCents)
        append(',')
        appendJsonString("topCategories", r.topCategories)
        append(',')
        appendJsonNumber("prevIncomeCents", r.prevIncomeCents)
        append(',')
        appendJsonNumber("prevExpenseCents", r.prevExpenseCents)
        append(',')
        if (r.analysisText == null) {
            append("\"analysisText\":null")
        } else {
            appendJsonString("analysisText", r.analysisText)
        }
        append(',')
        appendJsonString("status", r.status)
        append(',')
        appendJsonString("engine", r.engine)
        append(',')
        appendJsonString("contentVersion", r.contentVersion)
        append(',')
        appendJsonNumber("generatedAt", r.generatedAt)
        append(',')
        appendJsonString("localInsights", r.localInsights)
        append('}')
    }

    private fun StringBuilder.appendJsonString(key: String, value: String) {
        append('"').append(key).append("\":\"").append(escape(value)).append('"')
    }

    private fun StringBuilder.appendJsonNumber(key: String, value: Long) {
        append('"').append(key).append("\":").append(value)
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }

    private fun parseAccount(o: JsonObjectParser.Obj): BackupAccount = BackupAccount(
        id = o.long("id") ?: error("account.id"),
        name = o.string("name") ?: error("account.name"),
        createdAt = o.long("createdAt") ?: 0L,
        initialBalanceCents = o.long("initialBalanceCents") ?: 0L,
    )

    private fun parseTag(o: JsonObjectParser.Obj): BackupTag = BackupTag(
        id = o.long("id") ?: error("tag.id"),
        name = o.string("name") ?: error("tag.name"),
        parentId = o.longOrNull("parentId"),
        sortOrder = o.int("sortOrder") ?: 0,
        icon = o.stringOrNull("icon"),
        createdAt = o.long("createdAt") ?: 0L,
        updatedAt = o.long("updatedAt") ?: 0L,
    )

    private fun parseTransaction(o: JsonObjectParser.Obj): BackupTransaction = BackupTransaction(
        id = o.long("id") ?: error("tx.id"),
        amountCents = o.long("amountCents") ?: error("tx.amountCents"),
        type = o.string("type") ?: error("tx.type"),
        note = o.stringOrNull("note"),
        tagId = o.longOrNull("tagId"),
        accountId = o.longOrNull("accountId"),
        occurredAt = o.long("occurredAt") ?: 0L,
        createdAt = o.long("createdAt") ?: 0L,
    )

    private fun parseTransfer(o: JsonObjectParser.Obj): BackupTransfer = BackupTransfer(
        id = o.long("id") ?: error("tf.id"),
        fromAccountId = o.long("fromAccountId") ?: error("tf.from"),
        toAccountId = o.long("toAccountId") ?: error("tf.to"),
        amountCents = o.long("amountCents") ?: error("tf.amount"),
        note = o.stringOrNull("note"),
        occurredAt = o.long("occurredAt") ?: 0L,
        createdAt = o.long("createdAt") ?: 0L,
    )

    private fun parseSubscription(o: JsonObjectParser.Obj): BackupSubscription = BackupSubscription(
        id = o.long("id") ?: error("sub.id"),
        name = o.string("name") ?: error("sub.name"),
        amountCents = o.long("amountCents") ?: 0L,
        billingCycle = o.string("billingCycle") ?: error("sub.cycle"),
        note = o.stringOrNull("note"),
        isActive = o.bool("isActive") ?: true,
        billingStartAt = o.long("billingStartAt") ?: 0L,
        lastPostedDueAt = o.longOrNull("lastPostedDueAt"),
        lastPostedAt = o.longOrNull("lastPostedAt"),
        createdAt = o.long("createdAt") ?: 0L,
        updatedAt = o.long("updatedAt") ?: 0L,
    )

    private fun parseReport(o: JsonObjectParser.Obj): BackupReport = BackupReport(
        id = o.long("id") ?: error("report.id"),
        reportType = o.string("reportType") ?: error("report.type"),
        periodKey = o.string("periodKey") ?: error("report.key"),
        windowStartMs = o.long("windowStartMs") ?: 0L,
        windowEndMs = o.long("windowEndMs") ?: 0L,
        incomeCents = o.long("incomeCents") ?: 0L,
        expenseCents = o.long("expenseCents") ?: 0L,
        topCategories = o.string("topCategories") ?: "",
        prevIncomeCents = o.long("prevIncomeCents") ?: 0L,
        prevExpenseCents = o.long("prevExpenseCents") ?: 0L,
        analysisText = o.stringOrNull("analysisText"),
        status = o.string("status") ?: "",
        engine = o.string("engine") ?: "",
        contentVersion = o.string("contentVersion") ?: "",
        generatedAt = o.long("generatedAt") ?: 0L,
        localInsights = o.string("localInsights") ?: "",
    )
}
