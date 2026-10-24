package app.foodko.domain

import app.foodko.data.model.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodIndexTest {

    private fun food(id: String, label: String = id, mergedInto: String? = null, archived: Boolean = false) =
        Food(id = id, label = label, labelLower = normalizeForSearch(label), mergedInto = mergedInto, archived = archived)

    private val index = FoodIndex(
        listOf(
            food("c", "Zebra"),
            food("b", mergedInto = "c"),
            food("a", mergedInto = "b"),
            food("x", "Apple"),
            food("old", "Old", archived = true),
        ),
    )

    @Test
    fun `follows redirect chains to the end`() {
        assertEquals("c", index.resolveId("a"))
        assertEquals("c", index.resolveId("b"))
        assertEquals("c", index.resolveId("c"))
        assertEquals("Zebra", index.resolve("a")!!.label)
    }

    @Test
    fun `unknown ids pass through`() {
        assertEquals("ghost", index.resolveId("ghost"))
        assertEquals(null, index.resolve("ghost"))
    }

    @Test
    fun `visible excludes redirects and archived, sorted by label`() {
        assertEquals(listOf("x", "c"), index.visible.map { it.id })
        assertEquals(listOf("old"), index.archived.map { it.id })
    }

    @Test
    fun `redirectsAmong reports only ids that change`() {
        assertEquals(mapOf("a" to "c", "b" to "c"), index.redirectsAmong(listOf("a", "b", "c", "x", "a")))
        assertTrue(index.redirectsAmong(listOf("c", "x")).isEmpty())
        assertTrue(index.isRedirected("a"))
        assertFalse(index.isRedirected("c"))
    }

    @Test
    fun `search matches all words in any order, ignoring case and diacritics`() {
        val idx = FoodIndex(
            listOf(
                food("1", "Skyr vanilkový"),
                food("2", "Skyr natural"),
                food("3", "Vanilkový puding"),
                food("4", "Old skyr", archived = true),
            ),
        )
        assertEquals(setOf("1", "2"), idx.search("skyr").map { it.id }.toSet())
        assertEquals(setOf("1"), idx.search("VAN skyr").map { it.id }.toSet())
        assertEquals(setOf("1", "3"), idx.search("vanilkovy").map { it.id }.toSet())
        assertEquals(setOf("4"), idx.search("skyr", idx.archived).map { it.id }.toSet())
        assertEquals(idx.visible, idx.search("   "))
        assertTrue(idx.search("skyr puding").isEmpty())
    }

    @Test
    fun `search over thousands of foods is instant`() {
        val many = (1..5000).map { food("f$it", "Potravina číslo $it s dlhším názvom") }
        val idx = FoodIndex(many)
        val start = System.nanoTime()
        repeat(20) { idx.search("cislo 49") }
        val perSearchMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("one search took $perSearchMs ms", perSearchMs < 50)
    }

    @Test
    fun `cycles terminate`() {
        val cyclic = FoodIndex(listOf(food("p", mergedInto = "q"), food("q", mergedInto = "p")))
        cyclic.resolveId("p") // must not hang
    }

    @Test
    fun `search normalisation strips diacritics and case`() {
        assertEquals("skyr vanilkovy", normalizeForSearch("  Skyr Vanilkový "))
        assertTrue(normalizeForSearch("Šošovica").contains("sosovica"))
    }
}
