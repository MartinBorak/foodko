package app.foodko.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.foodko.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Decimal input; accepts ',' and '.', parsed with [app.foodko.domain.Numbers.parse]. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    )
}

/** Date and time of an [Instant] as two buttons opening Material pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: Instant,
    onValueChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val zoned = value.atZone(zone)
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Text(" " + formatDate(zoned.toLocalDate(), locale))
        }
        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Text(" " + formatTime(value, locale))
        }
    }

    if (showDate) {
        // DatePicker works in UTC-midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = zoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(date.atTime(zoned.toLocalTime()).atZone(zone).toInstant())
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date: LocalDate = zoned.toLocalDate()
                    onValueChange(date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant())
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}
