package app.foodko.ui

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.foodko.AppContainer
import app.foodko.R
import app.foodko.ui.foods.FoodFormScreen
import app.foodko.ui.foods.FoodsScreen
import app.foodko.ui.foods.MergeScreen
import app.foodko.ui.meal.MealFormScreen
import app.foodko.ui.settings.SettingsScreen
import app.foodko.ui.signin.AccessDeniedScreen
import app.foodko.ui.signin.SignInScreen
import app.foodko.ui.stats.StatsScreen
import app.foodko.ui.today.TodayScreen
import app.foodko.ui.weight.WeightScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

object Routes {
    const val TODAY = "today"
    const val FOODS = "foods"
    const val WEIGHT = "weight"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    const val MEAL = "meal?mealId={mealId}&foodId={foodId}&day={day}"
    fun meal(mealId: String? = null, foodId: String? = null, day: LocalDate? = null) =
        "meal?mealId=${mealId.orEmpty()}&foodId=${foodId.orEmpty()}&day=${day?.toEpochDay() ?: -1L}"

    const val FOOD = "food?foodId={foodId}&pick={pick}&label={label}"
    fun food(foodId: String? = null, pick: Boolean = false, label: String? = null) =
        "food?foodId=${foodId.orEmpty()}&pick=$pick&label=${Uri.encode(label.orEmpty())}"

    const val MERGE = "merge/{ids}"
    fun merge(ids: Collection<String>) = "merge/${ids.joinToString(",")}"

    /** Key in the previous back stack entry's SavedStateHandle for a food created from the picker. */
    const val PICKED_FOOD_ID = "pickedFoodId"
}

@Composable
fun FoodkoRoot(container: AppContainer) {
    val user by container.auth.user.collectAsStateWithLifecycle(initialValue = container.auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser = user

    if (currentUser == null) {
        LaunchedEffect(Unit) { container.closeSession() }
        SignInScreen(container.auth)
        return
    }

    val session = remember(currentUser.uid) { container.sessionFor(currentUser.uid) }
    val denied by session.accessDenied.collectAsStateWithLifecycle()
    val settings by session.userSettings.collectAsStateWithLifecycle()

    LaunchedEffect(settings?.language) { applyAppLanguage(context, settings?.language) }

    if (denied) {
        AccessDeniedScreen(email = currentUser.email) {
            scope.launch { container.auth.signOut(context) }
        }
        return
    }

    CompositionLocalProvider(LocalSession provides session) {
        MainNavigation()
    }
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.tab_today, Icons.Default.Today),
    Tab(Routes.FOODS, R.string.tab_foods, Icons.Default.Restaurant),
    Tab(Routes.WEIGHT, R.string.tab_weight, Icons.Default.MonitorWeight),
    Tab(Routes.STATS, R.string.tab_stats, Icons.Default.BarChart),
)

@Composable
private fun MainNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabs = tabs.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onAddMeal = { day -> navController.navigate(Routes.meal(day = day)) },
                    onEditMeal = { id -> navController.navigate(Routes.meal(mealId = id)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.FOODS) {
                FoodsScreen(
                    onAddFood = { navController.navigate(Routes.food()) },
                    onEditFood = { id -> navController.navigate(Routes.food(foodId = id)) },
                    onMerge = { ids -> navController.navigate(Routes.merge(ids)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.WEIGHT) {
                WeightScreen(onSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.STATS) {
                StatsScreen(onSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.MEAL,
                arguments = listOf(
                    navArgument("mealId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("foodId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val args = entry.arguments
                val day = args?.getLong("day")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
                MealFormScreen(
                    mealId = args?.getString("mealId")?.ifEmpty { null },
                    initialFoodId = args?.getString("foodId")?.ifEmpty { null },
                    day = day,
                    pickedFoodId = entry.savedStateHandle.getStateFlow<String?>(Routes.PICKED_FOOD_ID, null),
                    onConsumePickedFood = { entry.savedStateHandle[Routes.PICKED_FOOD_ID] = null },
                    onCreateFood = { label -> navController.navigate(Routes.food(pick = true, label = label)) },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                Routes.FOOD,
                arguments = listOf(
                    navArgument("foodId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("pick") { type = NavType.BoolType; defaultValue = false },
                    navArgument("label") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                val pick = args?.getBoolean("pick") ?: false
                FoodFormScreen(
                    foodId = args?.getString("foodId")?.ifEmpty { null },
                    prefillLabel = args?.getString("label").orEmpty(),
                    pickedFoodId = entry.savedStateHandle.getStateFlow<String?>(Routes.PICKED_FOOD_ID, null),
                    onConsumePickedFood = { entry.savedStateHandle[Routes.PICKED_FOOD_ID] = null },
                    onCreateFood = { label -> navController.navigate(Routes.food(pick = true, label = label)) },
                    onSaved = { id ->
                        if (pick) navController.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_FOOD_ID, id)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.MERGE,
                arguments = listOf(navArgument("ids") { type = NavType.StringType }),
            ) { entry ->
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').filter { it.isNotBlank() }
                MergeScreen(ids = ids, onDone = { navController.popBackStack() })
            }
        }
    }
}
