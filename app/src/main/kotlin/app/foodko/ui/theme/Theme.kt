package app.foodko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.foodko.domain.TargetKind

@Composable
fun FoodkoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // minSdk 33, so dynamic colour (wallpaper-based) is always available.
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * "On the way" blue: a total that has not crossed its target yet. Fixed rather than the primary
 * role, which follows the wallpaper and could come out red or green and blur the meaning.
 */
@Composable
fun progressColor(): Color = if (isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF1976D2)

/** "Goal reached" green: protein and fibre at or past their target. Material 3 has no success role. */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

/** The colour of a total that has [crossed][TargetKind.crossed] its target: red past a limit, green for a goal. */
@Composable
fun crossedColor(kind: TargetKind): Color = if (kind == TargetKind.Goal) successColor() else dangerColor()

/**
 * "Over the target" red. Dynamic dark colour turns the error role into a pale pink that hardly
 * reads as red on a card, so dark uses a saturated red that still has 4.5:1 contrast there.
 */
@Composable
fun dangerColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFF7369) else MaterialTheme.colorScheme.error
