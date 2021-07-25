package com.kr.commons.extensions

import android.content.Context
import android.text.format.DateFormat
import com.kr.commons.helpers.DATE_FORMAT_ONE
import java.text.DecimalFormat
import java.util.*
import kotlin.math.log10

fun Long.formatSize(): String {
    if (this <= 0) {
        return "0 B"
    }

    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    val digitGroups = (log10(toDouble()) / log10(1024.0)).toInt()
    return "${DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

fun Long.formatDate(context: Context, dateFormat: String? = null, timeFormat: String? = null): String {
    val useDateFormat = dateFormat ?: DATE_FORMAT_ONE
    val useTimeFormat = timeFormat ?: context.getTimeFormat()
    val cal = Calendar.getInstance(Locale.ENGLISH)
    cal.timeInMillis = this
    return DateFormat.format("$useDateFormat, $useTimeFormat", cal).toString()
}
