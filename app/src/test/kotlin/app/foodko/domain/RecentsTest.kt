package app.foodko.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {

    @Test
    fun `new food goes to the front`() {
        assertEquals(listOf("c", "a", "b"), pushRecent(listOf("a", "b"), "c"))
    }

    @Test
    fun `re-used food moves to the front instead of duplicating`() {
        assertEquals(listOf("b", "a", "c"), pushRecent(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a", "b"), pushRecent(listOf("a", "b"), "a"))
    }

    @Test
    fun `list is capped at the limit, dropping the oldest`() {
        val full = (1..RECENT_LIMIT).map { "f$it" }
        val next = pushRecent(full, "new")
        assertEquals(RECENT_LIMIT, next.size)
        assertEquals("new", next.first())
        assertEquals("f${RECENT_LIMIT - 1}", next.last())
    }
}
