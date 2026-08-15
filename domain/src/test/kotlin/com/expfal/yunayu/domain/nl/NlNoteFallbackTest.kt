package com.expfal.yunayu.domain.nl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [NlNoteFallback] 的 JVM 单元测试。 */
class NlNoteFallbackTest {

    @Test
    fun `strips amount and filler words`() {
        assertEquals("午饭", NlNoteFallback.extractNote("午饭花了20块", null))
    }

    @Test
    fun `strips date and amount`() {
        assertEquals("打车", NlNoteFallback.extractNote("昨天打车12元", null))
    }

    @Test
    fun `strips tag phrase and its sub variants`() {
        assertEquals("午饭", NlNoteFallback.extractNote("餐饮午饭20块", "生活·餐饮"))
        assertEquals("午饭", NlNoteFallback.extractNote("生活·餐饮午饭20块", "生活·餐饮"))
    }

    @Test
    fun `extracts core phrase from income sentence`() {
        assertEquals("奖学金", NlNoteFallback.extractNote("收到奖学金2000", null))
    }

    @Test
    fun `truncates note to eight chars`() {
        assertEquals(
            "十二个月健身房年",
            NlNoteFallback.extractNote("交了十二个月的健身房年费共2800元", null),
        )
    }

    @Test
    fun `returns null for pure amount or pure date`() {
        assertNull(NlNoteFallback.extractNote("20块", null))
        assertNull(NlNoteFallback.extractNote("昨天", null))
        assertNull(NlNoteFallback.extractNote("2024-01-01", null))
    }

    @Test
    fun `returns null when stripped text is shorter than two chars`() {
        assertNull(NlNoteFallback.extractNote("吧", null))
    }

    @Test
    fun `returns null for null input`() {
        assertNull(NlNoteFallback.extractNote(null, null))
    }

    @Test
    fun `strips amount variants`() {
        assertNull(NlNoteFallback.extractNote("20块5", null))
        assertEquals("午饭", NlNoteFallback.extractNote("¥15.5午饭", null))
        assertNull(NlNoteFallback.extractNote("1万", null))
        assertNull(NlNoteFallback.extractNote("2k", null))
    }

    @Test
    fun `keeps exactly two chars above lower bound`() {
        assertEquals("打车", NlNoteFallback.extractNote("打车5元", null))
    }

    @Test
    fun `keeps daily necessities after single filler removal`() {
        assertEquals("日用品", NlNoteFallback.extractNote("日用品30元", null))
    }

    @Test
    fun `strips non standard dates without residue`() {
        assertEquals("午饭", NlNoteFallback.extractNote("2024-1-1午饭", null))
        assertEquals("午饭", NlNoteFallback.extractNote("1月5日午饭", null))
    }

    @Test
    fun `treats blank tag phrase as no tag`() {
        assertEquals("午饭", NlNoteFallback.extractNote("午饭20块", ""))
    }

    @Test
    fun `truncateNote avoids isolated high surrogate`() {
        assertEquals("x😀😀😀", NlNoteFallback.truncateNote("x😀😀😀😀"))
    }
}
