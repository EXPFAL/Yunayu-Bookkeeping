package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [NlOutputParser] 的 JVM 单元测试。 */
class NlOutputParserTest {

    private val now = 1_700_000_000_000L
    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun `parses normal json`() {
        val draft = NlOutputParser.parseToDraft(
            "{\"amount\":\"20\",\"type\":\"expense\",\"tag\":\"生活·餐饮\",\"note\":\"午饭\",\"date\":\"今天\"}",
            now,
        )!!

        assertEquals(2000L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("生活·餐饮", draft.tagPhrase)
        assertEquals("午饭", draft.note)
        assertEquals(now, draft.occurredAtEpochMillis)
    }

    @Test
    fun `extracts json wrapped in surrounding text`() {
        val draft = NlOutputParser.parseToDraft(
            "解析结果如下：{\"amount\":35.5,\"type\":\"expense\",\"note\":\"买教材\"}",
            now,
        )!!

        assertEquals(3550L, draft.amountCents)
        assertEquals("买教材", draft.note)
        assertEquals(now, draft.occurredAtEpochMillis)
    }

    @Test
    fun `returns null when amount missing`() {
        assertNull(NlOutputParser.parseToDraft("{\"type\":\"expense\",\"note\":\"午饭\"}", now))
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(NlOutputParser.parseToDraft("没有任何 JSON 输出", now))
    }

    @Test
    fun `folds relative date`() {
        val draft = NlOutputParser.parseToDraft("{\"amount\":\"12\",\"date\":\"昨天\"}", now)!!

        assertEquals(now - dayMillis, draft.occurredAtEpochMillis)
    }

    @Test
    fun `recognizes income type`() {
        val draft = NlOutputParser.parseToDraft("{\"amount\":\"2000\",\"type\":\"income\"}", now)!!

        assertEquals(TransactionType.INCOME, draft.type)
    }

    @Test
    fun `defaults missing date to base time`() {
        val draft = NlOutputParser.parseToDraft("{\"amount\":\"20\"}", now)!!

        assertEquals(now, draft.occurredAtEpochMillis)
    }
}
