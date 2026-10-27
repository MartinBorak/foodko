package app.foodko.ui.foods

import app.foodko.MainDispatcherRule
import app.foodko.TestWorld
import app.foodko.data.model.Food
import app.foodko.food
import app.foodko.meal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FoodFormViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val skyr = food("skyr", "Skyr vanilkový", kcal = 63.0, carbs = 4.1, fat = 0.2, protein = 10.0, fibre = 0.0)
        .copy(createdBy = "someone-else", note = "Lidl")
    private val rice = food("rice", "Rice, dry", kcal = 350.0, carbs = 78.0, fat = 1.0, protein = 7.0, fibre = 1.5)
    private val stock = food("stock", "Stock cube", kcal = 200.0, carbs = null, fat = null, protein = 10.0, fibre = null)

    private val world = TestWorld(
        main.dispatcher,
        foods = listOf(skyr, rice, stock, food("oldskyr", mergedInto = "skyr"), food("gone", "Gone", archived = true)),
        meals = listOf(meal("m1", "skyr", 100.0), meal("m2", "oldskyr", 100.0), meal("m3", "rice", 100.0)),
    )
    private val index get() = world.session.foodIndex.value!!

    @After
    fun tearDown() = world.session.close()

    @Test
    fun `a new food is prefilled with the typed label and saved under the current user`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "Banán")
        assertFalse(vm.isEditing)
        assertFalse(vm.loading)
        assertEquals("Banán", vm.fields.label)
        assertFalse("kcal is required", vm.fields.isValid)

        vm.fields.kcal = "89"
        vm.fields.carbs = "23"
        vm.fields.note = "  ripe  "
        var savedId: String? = null
        vm.save { savedId = it }

        assertNotNull(savedId)
        val saved = world.foods.byId(savedId!!)
        assertEquals("Banán", saved.label)
        assertEquals("banan", saved.labelLower)
        assertEquals(89.0, saved.kcalPer100g, 1e-9)
        assertEquals(23.0, saved.carbsPer100g!!, 1e-9)
        assertNull(saved.fatPer100g)
        assertEquals("ripe", saved.note)
        assertEquals(world.uid, saved.createdBy)
    }

    @Test
    fun `duplicate detection ignores case, diacritics, archived foods and the food itself`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "")
        assertNull(vm.duplicateOf(index))
        vm.fields.label = "  SKYR VANILKOVY "
        assertEquals("skyr", vm.duplicateOf(index)!!.id)
        vm.fields.label = "gone"
        assertNull("archived foods are not offered as duplicates", vm.duplicateOf(index))

        val editing = FoodFormViewModel(world.session, foodId = "skyr", prefillLabel = "")
        assertNull("a food is not its own duplicate", editing.duplicateOf(index))
    }

    @Test
    fun `a duplicate label blocks saving, the food's own label does not`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "skyr vanilkovy")
        vm.fields.kcal = "60"
        assertTrue(vm.fields.isValid)
        assertFalse(vm.canSave(index))
        vm.save { throw AssertionError("must not save a duplicate") }
        assertEquals(5, world.foods.state.value.size)

        vm.fields.label = "Gone"
        assertTrue("an archived food's label may be reused", vm.canSave(index))

        val editing = FoodFormViewModel(world.session, foodId = "skyr", prefillLabel = "")
        assertTrue(editing.canSave(index))
        editing.fields.label = "rice, DRY"
        assertFalse("renaming onto another food is blocked too", editing.canSave(index))
    }

    @Test
    fun `a new food suggests matching live foods from two characters`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "s")
        assertEquals(emptyList<Food>(), vm.suggestions(index))
        vm.fields.label = "st"
        assertEquals(listOf("stock"), vm.suggestions(index).map { it.id })
        vm.fields.label = "VANILK sky"
        assertEquals("every word, any order, no diacritics", listOf("skyr"), vm.suggestions(index).map { it.id })
        vm.fields.label = "gone"
        assertEquals("archived foods are not suggested", emptyList<Food>(), vm.suggestions(index))

        val editing = FoodFormViewModel(world.session, foodId = "skyr", prefillLabel = "")
        assertEquals("nothing is suggested while editing", emptyList<Food>(), editing.suggestions(index))
    }

    @Test
    fun `suggestions put the exact match first and stop at five`() = runTest {
        val rices = listOf("Brown rice", "Rice", "Rice noodles", "Basmati rice", "Wild rice", "Jasmine rice")
        val many = TestWorld(main.dispatcher, foods = rices.mapIndexed { i, label -> food("r$i", label) })
        try {
            val vm = FoodFormViewModel(many.session, foodId = null, prefillLabel = "rice")
            val labels = vm.suggestions(many.session.foodIndex.value!!).map { it.label }
            assertEquals(listOf("Rice", "Basmati rice", "Brown rice", "Jasmine rice", "Rice noodles"), labels)
        } finally {
            many.session.close()
        }
    }

    @Test
    fun `editing loads the values as text and keeps bookkeeping on save`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = "skyr", prefillLabel = "")
        assertTrue(vm.isEditing)
        assertFalse(vm.loading)
        assertEquals("Skyr vanilkový", vm.fields.label)
        assertEquals("63", vm.fields.kcal)
        assertEquals("4.1", vm.fields.carbs)
        assertEquals("0.2", vm.fields.fat)
        assertEquals("10", vm.fields.protein)
        assertEquals("0", vm.fields.fibre)
        assertEquals("Lidl", vm.fields.note)
        assertEquals("counts meals through redirects", 2, vm.myMealCount)

        vm.fields.kcal = "65"
        vm.fields.fibre = ""
        vm.save {}

        val saved = world.foods.byId("skyr")
        assertEquals(65.0, saved.kcalPer100g, 1e-9)
        assertNull(saved.fibrePer100g)
        assertEquals("someone-else", saved.createdBy)
        assertEquals(5, world.foods.state.value.size)
    }

    @Test
    fun `invalid fields block saving`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "X")
        vm.fields.kcal = "-5"
        assertFalse(vm.fields.kcalValid)
        assertFalse(vm.fields.isValid)
        vm.fields.kcal = "100"
        vm.fields.protein = "abc"
        assertFalse(vm.fields.isValid)
        vm.save { throw AssertionError("must not save") }
        assertEquals(5, world.foods.state.value.size)

        vm.fields.protein = "12,5"
        assertTrue(vm.fields.isValid)
        assertEquals(12.5, vm.fields.macroSum, 1e-9)
    }

    @Test
    fun `ingredient calculator composes per 100 g and fills the form`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "Risotto")
        assertEquals(FormMode.FORM, vm.mode)
        assertFalse(vm.back())

        vm.startIngredients()
        assertEquals(FormMode.INGREDIENTS, vm.mode)
        vm.startPickingIngredient()
        assertEquals(FormMode.PICKING_INGREDIENT, vm.mode)
        vm.addIngredient("rice")
        assertEquals(FormMode.INGREDIENTS, vm.mode)
        assertNull("grams not typed yet", vm.composed(index))

        vm.ingredients[0].grams = "200"
        assertNull("finished weight missing", vm.composed(index))
        vm.cookedGrams = "600"
        val r = vm.composed(index)!!
        assertEquals(700.0 / 6, r.kcal, 1e-9)
        assertEquals(14.0 / 6, r.protein!!, 1e-9)

        vm.startPickingIngredient()
        vm.addIngredient("stock")
        vm.ingredients[1].grams = "10"
        assertNull("a macro missing on any ingredient is missing", vm.composed(index)!!.carbs)

        vm.removeIngredient(vm.ingredients[1])
        vm.useComposed(index)
        assertEquals(FormMode.FORM, vm.mode)
        assertEquals("116.67", vm.fields.kcal)
        assertEquals("26", vm.fields.carbs)
        assertEquals("2.33", vm.fields.protein)
        assertTrue(vm.fields.isValid)
    }

    @Test
    fun `back steps through the calculator modes`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = null, prefillLabel = "")
        vm.startIngredients()
        vm.startPickingIngredient()
        assertTrue(vm.back())
        assertEquals(FormMode.INGREDIENTS, vm.mode)
        assertTrue(vm.back())
        assertEquals(FormMode.FORM, vm.mode)
        assertFalse(vm.back())
    }

    @Test
    fun `archiving and unarchiving flips the flag without touching anything else`() = runTest {
        val vm = FoodFormViewModel(world.session, foodId = "rice", prefillLabel = "")
        var done = 0
        vm.setArchived(true) { done++ }
        assertEquals(1, done)
        assertTrue(world.foods.byId("rice").archived)
        assertEquals(rice.kcalPer100g, world.foods.byId("rice").kcalPer100g, 1e-9)

        FoodFormViewModel(world.session, foodId = "rice", prefillLabel = "").setArchived(false) { done++ }
        assertEquals(2, done)
        assertFalse(world.foods.byId("rice").archived)

        FoodFormViewModel(world.session, foodId = null, prefillLabel = "").setArchived(true) { throw AssertionError() }
    }

    @Test
    fun `fields apply onto a base food`() {
        val f = FoodFields().apply { label = " Rice "; kcal = "350"; carbs = "78"; fat = ""; protein = "7"; fibre = "1.5" }
        val applied = f.applyTo(Food(id = "keep", createdBy = "me"))
        assertEquals("keep", applied.id)
        assertEquals("me", applied.createdBy)
        assertEquals("Rice", applied.label)
        assertEquals("rice", applied.labelLower)
        assertNull(applied.fatPer100g)
        assertNull(applied.note)
    }
}
