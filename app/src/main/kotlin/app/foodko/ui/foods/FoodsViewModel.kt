package app.foodko.ui.foods

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.foodko.UserSession

class FoodsViewModel(session: UserSession) : ViewModel() {
    val foodIndex = session.foodIndex
    var query by mutableStateOf("")
    var showArchived by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<String>())
        private set

    /** Entered via long-press or the "Merge duplicates…" menu item; left with the X. */
    var selecting by mutableStateOf(false)
        private set

    fun startSelecting() { selecting = true }

    fun toggle(id: String) {
        selecting = true
        selected = if (id in selected) selected - id else selected + id
    }

    fun clearSelection() {
        selected = emptySet()
        selecting = false
    }
}
