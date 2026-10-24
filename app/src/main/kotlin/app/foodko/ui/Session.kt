package app.foodko.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foodko.UserSession

val LocalSession = staticCompositionLocalOf<UserSession> { error("No signed-in session") }

/** ViewModel built from the current [UserSession]; survives recreation like any ViewModel. */
@Composable
inline fun <reified VM : ViewModel> sessionViewModel(
    key: String? = null,
    crossinline create: (UserSession) -> VM,
): VM {
    val session = LocalSession.current
    return viewModel(key = key) { create(session) }
}
