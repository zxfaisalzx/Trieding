package com.faisal.iltizam.ui

import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val moneyFmt = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private val qtyFmt = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

fun money(v: Double): String = "$" + moneyFmt.format(v)
fun signedMoney(v: Double): String = (if (v > 0) "+" else if (v < 0) "-" else "") + money(kotlin.math.abs(v))
fun pct(v: Double): String = (if (v > 0) "+" else "") + moneyFmt.format(v * 100) + "%"
fun qty(v: Int): String = qtyFmt.format(v)

/** يقبل الأرقام العربية والفاصلة العربية. */
fun parseNum(s: String): Double? = s.trim()
    .map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }
    .joinToString("")
    .replace('٫', '.')
    .replace(',', '.')
    .toDoubleOrNull()

fun parseInt(s: String): Int? = parseNum(s)?.takeIf { it >= 0 && it == Math.floor(it) }?.toInt()

val Gain = Color(0xFF2E9E5B)
val Loss = Color(0xFFD9534F)
fun pnlColor(v: Double, neutral: Color): Color = when {
    v > 0.0001 -> Gain
    v < -0.0001 -> Loss
    else -> neutral
}
