package app.foodko.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foodko.R
import app.foodko.appContainer
import app.foodko.domain.Numbers
import app.foodko.ui.LocalSession
import app.foodko.ui.applyAppLanguage
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.NumberField
import app.foodko.ui.currentAppLanguage
import kotlinx.coroutines.launch

private val languages = listOf("" to R.string.language_system, "en" to R.string.language_en, "sk" to R.string.language_sk)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val settings by session.userSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        // Local copies so typing doesn't round-trip through Firestore on every keystroke.
        var name by remember(s.displayName) { mutableStateOf(s.displayName.orEmpty()) }
        var target by remember(s.dailyKcalTarget) { mutableStateOf(Numbers.editable(s.dailyKcalTarget)) }
        var language by remember { mutableStateOf(s.language ?: currentAppLanguage(context)) }
        LaunchedEffect(s.language) { s.language?.let { language = it } }

        // Saved when a field loses focus and, as a fallback, when the screen is left while a
        // field is still focused (Back button). Writes go through the session scope so they
        // survive this composable being disposed.
        val latestName by rememberUpdatedState(name)
        val latestTarget by rememberUpdatedState(target)
        val latestSettings by rememberUpdatedState(s)
        val saveName = {
            val value = latestName.trim()
            if (value != latestSettings.displayName.orEmpty()) session.scope.launch { session.settings.setDisplayName(value) }
        }
        val saveTarget = {
            val text = latestTarget
            val parsed = Numbers.parse(text)?.takeIf { it > 0 }
            if ((text.isBlank() || parsed != null) && parsed != latestSettings.dailyKcalTarget) {
                session.scope.launch { session.settings.setDailyKcalTarget(parsed) }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                saveName()
                saveTarget()
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (!f.isFocused) saveName() },
            )
            Spacer(Modifier.height(16.dp))
            val parsedTarget = Numbers.parse(target)?.takeIf { it > 0 }
            NumberField(
                value = target,
                onValueChange = { target = it },
                label = stringResource(R.string.daily_kcal_target),
                suffix = stringResource(R.string.kcal_unit),
                supportingText = stringResource(R.string.daily_kcal_target_hint),
                isError = target.isNotBlank() && parsedTarget == null,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (!f.isFocused) saveTarget() },
            )
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                languages.forEachIndexed { i, (tag, labelRes) ->
                    SegmentedButton(
                        selected = language == tag,
                        onClick = {
                            language = tag
                            scope.launch { session.settings.setLanguage(tag) }
                            applyAppLanguage(context, tag)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = languages.size),
                    ) { Text(stringResource(labelRes)) }
                }
            }
            Spacer(Modifier.height(32.dp))

            Text(
                stringResource(R.string.signed_in_as, container.auth.currentUser?.email ?: "?"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { container.auth.signOut(context) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sign_out)) }
        }
    }
}
