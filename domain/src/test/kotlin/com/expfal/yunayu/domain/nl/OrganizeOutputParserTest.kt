package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.nl.model.OrganizeSuggestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [OrganizeOutputParser] 的 JVM 单元测试。 */
class OrganizeOutputParserTest {

    @Test
    fun `parses attach and create suggestions`() {
        val raw =
            """[{"record_id":1,"action":"ATTACH","tag_name":"学习"},{"record_id":2,"action":"CREATE","tag_name":"兼职","root_name":"收入"}]"""

        val result = OrganizeOutputParser.parse(raw, setOf(1L, 2L))

        assertEquals(2, result.size)
        assertEquals(OrganizeSuggestion(1L, Action.ATTACH, "学习", null), result[0])
        assertEquals(OrganizeSuggestion(2L, Action.CREATE, "兼职", "收入"), result[1])
    }

    @Test
    fun `skips item with invalid record id and keeps valid`() {
        val raw =
            """[{"record_id":1,"action":"ATTACH","tag_name":"学习"},{"record_id":99,"action":"ATTACH","tag_name":"生活"}]"""

        val result = OrganizeOutputParser.parse(raw, setOf(1L))

        assertEquals(1, result.size)
        assertEquals(1L, result.single().recordId)
    }

    @Test
    fun `skips item with missing fields and keeps valid`() {
        val raw =
            """[{"record_id":1},{"record_id":2,"action":"CREATE","tag_name":"兼职","root_name":"收入"}]"""

        val result = OrganizeOutputParser.parse(raw, setOf(1L, 2L))

        assertEquals(1, result.size)
        assertEquals(2L, result.single().recordId)
    }

    @Test
    fun `skips item with illegal action`() {
        val raw = """[{"record_id":1,"action":"REMOVE","tag_name":"学习"}]"""

        assertTrue(OrganizeOutputParser.parse(raw, setOf(1L)).isEmpty())
    }

    @Test
    fun `unescapes quotes in tag name`() {
        val raw = """[{"record_id":1,"action":"ATTACH","tag_name":"兼职\"外卖\""}]"""

        val result = OrganizeOutputParser.parse(raw, setOf(1L))

        assertEquals("兼职\"外卖\"", result.single().tagName)
    }

    @Test
    fun `returns empty for blank or non array output`() {
        assertTrue(OrganizeOutputParser.parse("", setOf(1L)).isEmpty())
        assertTrue(OrganizeOutputParser.parse("没有任何数组", setOf(1L)).isEmpty())
    }

    @Test
    fun `extracts array from surrounding prose`() {
        val raw = "以下是建议：\n[{\"record_id\":1,\"action\":\"ATTACH\",\"tag_name\":\"学习\"}]\n希望有帮助"

        val result = OrganizeOutputParser.parse(raw, setOf(1L))

        assertEquals(1, result.size)
        assertEquals("学习", result.single().tagName)
    }
}
