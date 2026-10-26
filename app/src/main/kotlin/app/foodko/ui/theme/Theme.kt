package app.foodko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

/** "Within the target" green. Material 3 has no success role and dynamic colour has no green. */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

/** "Over, but that's fine" amber: protein and fibre past their target. */
@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFFF9A825)

/**
 * "Over the target" red. Dynamic dark colour turns the error role into a pale pink that hardly
 * reads as red on a card, so dark uses a saturated red that still has 4.5:1 contrast there.
 */
@Composable
fun dangerColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFF7369) else MaterialTheme.colorScheme.error
