package app.foodko.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class NumbersTest {

    private val en = Locale.US
    private val sk = Locale.forLanguageTag("sk")

    @Test
    fun `whole numbers have no decimal part`() {
        assertEquals("150", Numbers.entered(150.0, en))
        assertEquals("150", Numbers.computed(150.0, en))
        assertEquals("0", Numbers.computed(0.0, en))
    }

    @Test
    fun `computed values round to one decimal and trim zeros`() {
        assertEquals("94.5", Numbers.computed(94.5, en))
        assertEquals("94.6", Numbers.computed(94.56, en))
        assertEquals("95", Numbers.computed(94.96, en))
    }

    @Test
    fun `kcal are whole numbers`() {
        assertEquals("1633", Numbers.kcal(1633.4, en))
        assertEquals("217", Numbers.kcal(216.6, en))
        assertEquals("-1178", Numbers.kcal(-1178.4, en))
    }

    @Test
    fun `entered values keep up to two decimals`() {
        assertEquals("3.25", Numbers.entered(3.25, en))
        assertEquals("72.4", Numbers.entered(72.40, en))
        assertEquals("1.33", Numbers.entered(1.3333, en))
    }

    @Test
    fun `slovak locale uses a comma`() {
        assertEquals("72,4", Numbers.entered(72.4, sk))
    }

    @Test
    fun `parse accepts comma and dot`() {
        assertEquals(72.4, Numbers.parse("72,4")!!, 1e-9)
        assertEquals(72.4, Numbers.parse(" 72.4 ")!!, 1e-9)
        assertEquals(150.0, Numbers.parse("150")!!, 1e-9)
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse("abc"))
        assertNull(Numbers.parse("NaN"))
    }

    @Test
    fun `editable text follows the locale and round-trips through parse`() {
        assertEquals("72.4", Numbers.editable(72.4, en))
        assertEquals("72,4", Numbers.editable(72.4, sk))
        assertEquals(72.4, Numbers.parse(Numbers.editable(72.4, sk))!!, 1e-9)
        assertEquals("", Numbers.editable(null, en))
    }
}
