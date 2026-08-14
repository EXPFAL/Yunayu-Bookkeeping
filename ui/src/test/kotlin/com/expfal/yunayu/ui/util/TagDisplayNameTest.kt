package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.model.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [tagDisplayName] 的 JVM 单元测试。 */
class TagDisplayNameTest {

    @Test
    fun `root tag returns its name`() {
        assertEquals("学习", tagDisplayName(Tag(id = 1, name = "学习"), emptyMap()))
    }

    @Test
    fun `sub tag with parent mapping returns parent dot child`() {
        val tag = Tag(id = 5, name = "教材", parentId = 1L)
        assertEquals("学习·教材", tagDisplayName(tag, mapOf(1L to "学习")))
    }

    @Test
    fun `sub tag without parent mapping falls back to its name`() {
        val tag = Tag(id = 5, name = "教材", parentId = 1L)
        assertEquals("教材", tagDisplayName(tag, emptyMap()))
    }

    @Test
    fun `sub tag with unrelated mapping falls back to its name`() {
        val tag = Tag(id = 5, name = "教材", parentId = 1L)
        assertEquals("教材", tagDisplayName(tag, mapOf(2L to "社交")))
    }
}
