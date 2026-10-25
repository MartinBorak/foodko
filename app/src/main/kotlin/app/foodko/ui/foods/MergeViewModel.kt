package app.foodko.ui.foods

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.data.model.Food
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MergeViewModel(private val session: UserSession, private val ids: List<String>) : ViewModel() {

    var foods by mutableStateOf<List<Food>>(emptyList())
        private set
    var usage by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var survivorId by mutableStateOf<String?>(null)
        private set
    val fields = FoodFields()
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val index = session.foodIndex.filterNotNull().first()
            val meals = session.allMeals.filterNotNull().first()
            foods = ids.mapNotNull { index.resolve(it) }.distinctBy { it.id }
            usage = meals.groupingBy { index.resolveId(it.foodId) }.eachCount()
            // Default survivor: most used in my meals, then the oldest.
            foods.maxWithOrNull(
                compareBy<Food> { usage[it.id] ?: 0 }.thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE },
            )?.let { choose(it.id) }
            loading = false
        }
    }

    fun choose(id: String) {
        survivorId = id
        foods.firstOrNull { it.id == id }?.let(fields::load)
    }

    val canSave: Boolean get() = foods.size >= 2 && survivorId != null && fields.isValid && !saving

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        val survivor = foods.firstOrNull { it.id == survivorId } ?: return
        saving = true
        viewModelScope.launch {
            session.mergeFoods(
                survivor = fields.applyTo(survivor),
                mergedIds = foods.map { it.id }.filter { it != survivor.id },
            )
            onDone()
        }
    }
}
