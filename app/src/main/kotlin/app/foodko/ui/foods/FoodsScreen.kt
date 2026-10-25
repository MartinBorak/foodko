package app.foodko.ui.foods

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foodko.R
import app.foodko.UserSession
import app.foodko.data.model.Food
import app.foodko.domain.Numbers
import app.foodko.ui.components.EmptyText
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.currentLocale
import app.foodko.ui.sessionViewModel

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoodsScreen(
    onAddFood: () -> Unit,
    onEditFood: (String) -> Unit,
    onMerge: (Collection<String>) -> Unit,
    onSettings: () -> Unit,
) {
    val vm = sessionViewModel { FoodsViewModel(it) }
    val index by vm.foodIndex.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            if (vm.selecting) {
                TopAppBar(
                    title = {
                        Text(
                            if (vm.selected.isEmpty()) stringResource(R.string.select_foods_to_merge)
                            else pluralStringResource(R.plurals.selected_count, vm.selected.size, vm.selected.size),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { val ids = vm.selected; vm.clearSelection(); onMerge(ids) }, enabled = vm.selected.size >= 2) {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = stringResource(R.string.merge))
                        }
                    },
                )
            } else {
                var menuOpen by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text(stringResource(R.string.tab_foods)) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.merge_duplicates)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null) },
                                onClick = { menuOpen = false; vm.startSelecting() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (vm.showArchived) R.string.hide_archived else R.string.show_archived)) },
                                leadingIcon = {
                                    Icon(if (vm.showArchived) Icons.Default.Unarchive else Icons.Default.Inventory2, contentDescription = null)
                                },
                                onClick = { menuOpen = false; vm.showArchived = !vm.showArchived },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!vm.selecting) {
                FloatingActionButton(onClick = onAddFood) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_food))
                }
            }
        },
    ) { padding ->
        val currentIndex = index
        if (currentIndex == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val source = if (vm.showArchived) currentIndex.archived else currentIndex.visible
        val foods = currentIndex.search(vm.query, source)
        val perHundred = stringResource(R.string.kcal_per_100g_short)

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_foods)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (vm.showArchived) {
                Text(
                    stringResource(R.string.showing_archived),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (foods.isEmpty()) {
                EmptyText(stringResource(if (source.isEmpty()) R.string.foods_empty else R.string.no_foods_found))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(foods, key = { it.id }) { food ->
                        FoodRow(
                            food = food,
                            perHundred = perHundred,
                            locale = locale,
                            selecting = vm.selecting,
                            selected = food.id in vm.selected,
                            onClick = { if (vm.selecting) vm.toggle(food.id) else onEditFood(food.id) },
                            onLongClick = { vm.toggle(food.id) },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(
    food: Food,
    perHundred: String,
    locale: java.util.Locale,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = stringResource(R.string.protein_short)
    val c = stringResource(R.string.carbs_short)
    val f = stringResource(R.string.fat_short)
    val macros = listOfNotNull(
        food.proteinPer100g?.let { "$p ${Numbers.entered(it, locale)}" },
        food.carbsPer100g?.let { "$c ${Numbers.entered(it, locale)}" },
        food.fatPer100g?.let { "$f ${Numbers.entered(it, locale)}" },
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = { Text(food.label) },
        supportingContent = {
            Column {
                Text(listOf("${Numbers.entered(food.kcalPer100g, locale)} $perHundred", macros).filter { it.isNotEmpty() }.joinToString(" · "))
                food.note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        trailingContent = if (selecting) ({ Checkbox(checked = selected, onCheckedChange = { onClick() }) }) else null,
    )
}
