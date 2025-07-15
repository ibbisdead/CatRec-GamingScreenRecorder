package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.*

@Composable
fun DateTimePickerDialog(
    initialTime: Long,
    onDismiss: () -> Unit,
    onTimeSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = if (initialTime > 0L) initialTime else System.currentTimeMillis()
        }
        val dateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    onTimeSelected(calendar.timeInMillis)
                    onDismiss()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).apply {
                setOnCancelListener { onDismiss() }
            }.show()
        }
        val datePicker = DatePickerDialog(
            context,
            dateListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.setOnCancelListener { onDismiss() }
        datePicker.show()
        onDispose { }
    }
} 