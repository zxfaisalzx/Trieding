package com.faisal.iltizam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faisal.iltizam.domain.Engine
import com.faisal.iltizam.domain.Position
import com.faisal.iltizam.domain.Tranche
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Dialog {
    data class Execute(val tranche: Tranche, val isStop: Boolean) : Dialog()
    data object FreeSale : Dialog()
    data object ManualPrice : Dialog()
    data object ConfirmDelete : Dialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    p: Position,
    vm: AppViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val neutral = MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.symbol, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل الخطة") }
                    IconButton(onClick = { dialog = Dialog.ConfirmDelete }) { Icon(Icons.Default.Delete, "حذف") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("السعر الحالي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(p.lastPrice?.let { money(it) } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            p.lastUpdated?.let {
                                Text("آخر تحديث ${timeFmt(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { dialog = Dialog.ManualPrice }) { Text("أدخل السعر يدوياً") }
                    }
                    Row {
                        Stat("الكمية الباقية", "${qty(p.remainingQty)} من ${qty(p.qty)}", neutral, Modifier.weight(1f))
                        Stat("متوسط الشراء", money(p.avgCost), neutral, Modifier.weight(1f))
                    }
                    Row {
                        Stat("رأس المال", money(p.capital), neutral, Modifier.weight(1f))
                        Stat("أعلى سعر بعد الشراء", money(p.peakPrice), neutral, Modifier.weight(1f))
                    }
                    Row {
                        Stat("ربح غير محقق", signedMoney(p.unrealizedProfit), pnlColor(p.unrealizedProfit, neutral), Modifier.weight(1f))
                        Stat("ربح محقق", signedMoney(p.realizedProfit), pnlColor(p.realizedProfit, neutral), Modifier.weight(1f))
                    }
                    Row {
                        Stat(
                            if (p.lockedProfit >= 0) "ربح مؤمَّن بالوقف" else "أقصى خسارة لو ضرب الوقف",
                            signedMoney(p.lockedProfit), pnlColor(p.lockedProfit, neutral), Modifier.weight(1f)
                        )
                        val trail = Engine.trailPercent(p)
                        Stat(
                            "الوقف المتحرك",
                            when {
                                !p.smartTrailing -> "مطفي"
                                trail == null -> "ينتظر نص الطريق"
                                else -> "${(trail * 100).toInt()}% تحت أعلى سعر"
                            },
                            neutral, Modifier.weight(1f)
                        )
                    }
                }
            }

            if (!p.isClosed && p.highestStop != null) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "تأكد إن أمر الوقف عند الوسيط على ${money(p.highestStop!!)}. الأمر الحقيقي يحميك حتى لو جوالك مقفل.",
                        Modifier.padding(16.dp), fontSize = 13.sp
                    )
                }
            }

            PlanCard("خطة البيع (الأهداف)", p.targets.sortedBy { it.price }, p, isStop = false) {
                dialog = Dialog.Execute(it, false)
            }
            PlanCard("خطة الوقف", p.stops.sortedByDescending { it.price }, p, isStop = true) {
                dialog = Dialog.Execute(it, true)
            }

            if (!p.isClosed) {
                OutlinedButton(onClick = { dialog = Dialog.FreeSale }, modifier = Modifier.fillMaxWidth()) {
                    Text("بيع خارج الخطة")
                }
            }

            if (p.sales.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("سجل البيع", fontWeight = FontWeight.Bold)
                        val planned = p.sales.filter { it.planned }.sumOf { it.qty }
                        Text(
                            "الالتزام بالخطة: ${qty(planned)} من ${qty(p.soldQty)} سهم بيعت حسب الخطة",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        p.sales.sortedByDescending { it.at }.forEach { s ->
                            HorizontalDivider()
                            Row {
                                Text("${qty(s.qty)} × ${money(s.price)}", Modifier.weight(1f))
                                Text(
                                    signedMoney((s.price - p.avgCost) * s.qty),
                                    color = pnlColor(s.price - p.avgCost, neutral)
                                )
                            }
                            Text(
                                "${dateFmt(s.at)} · ${if (s.planned) "حسب الخطة" else "خارج الخطة"}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (val d = dialog) {
        is Dialog.Execute -> NumberDialog(
            title = if (d.isStop) "بعت بالوقف؟" else "بعت عند الهدف؟",
            text = "سجّل سعر البيع الفعلي لـ ${qty(minOf(d.tranche.qty, p.remainingQty))} سهم.",
            initial = trimPrice(if (d.isStop) (p.lastPrice ?: d.tranche.price) else d.tranche.price),
            confirm = "تم البيع",
            onConfirm = { vm.execute(p.id, d.tranche.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.ManualPrice -> NumberDialog(
            title = "السعر الحالي لـ ${p.symbol}",
            text = "يحدّث الوقف ويشيك على الأهداف بهذا السعر.",
            initial = p.lastPrice?.let { trimPrice(it) } ?: "",
            confirm = "تحديث",
            onConfirm = { vm.manualPrice(p.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.FreeSale -> FreeSaleDialog(p, onConfirm = { q, price -> vm.freeSale(p.id, q, price); dialog = null }, onDismiss = { dialog = null })
        Dialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("حذف ${p.symbol}؟") },
            text = { Text("بتنحذف الخطة وسجل البيع لهذا السهم.") },
            confirmButton = { TextButton(onClick = { vm.delete(p.id); dialog = null; onBack() }) { Text("حذف", color = Loss) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } },
        )
        null -> {}
    }
}

@Composable
private fun PlanCard(title: String, items: List<Tranche>, p: Position, isStop: Boolean, onExecute: (Tranche) -> Unit) {
    val neutral = MaterialTheme.colorScheme.onSurface
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (items.isEmpty()) {
                Text(
                    if (isStop) "ما حطيت وقف. هذا أخطر شي، عدّل الخطة وحط وقف." else "ما حطيت أهداف بيع.",
                    color = if (isStop) Loss else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
            }
            items.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${qty(t.qty)} سهم عند ${money(t.price)}", fontWeight = FontWeight.SemiBold)
                        val expected = (t.price - p.avgCost) * t.qty
                        val sub = when {
                            t.done -> "✓ نُفّذت عند ${money(t.donePrice ?: t.price)}"
                            isStop && t.price > t.originalPrice + 0.001 -> "رفعه الوقف المتحرك من ${money(t.originalPrice)}"
                            p.lastPrice != null -> "يبعد ${pct((t.price - p.lastPrice) / p.lastPrice)} عن السعر"
                            else -> ""
                        }
                        Text(
                            (if (expected >= 0) "ربح " else "خسارة ") + money(kotlin.math.abs(expected)) +
                                (if (sub.isNotEmpty()) " · $sub" else ""),
                            fontSize = 12.sp,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(expected, neutral)
                        )
                    }
                    if (!t.done && !p.isClosed) {
                        val reached = p.lastPrice?.let { if (isStop) it <= t.price else it >= t.price } == true
                        if (reached) Button(onClick = { onExecute(t) }) { Text("بعت") }
                        else FilledTonalButton(onClick = { onExecute(t) }) { Text("بعت") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    text: String,
    initial: String,
    confirm: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val parsed = parseNum(value)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(
                    value, { value = it }, label = { Text("السعر $") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun FreeSaleDialog(p: Position, onConfirm: (Int, Double) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(p.lastPrice?.let { trimPrice(it) } ?: "") }
    val qv = parseInt(q)?.takeIf { it in 1..p.remainingQty }
    val pv = parseNum(price)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيع خارج الخطة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ينحسب في السجل إنه خارج الخطة.", fontSize = 13.sp)
                OutlinedTextField(
                    q, { q = it }, label = { Text("الكمية (الباقي ${qty(p.remainingQty)})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    price, { price = it }, label = { Text("السعر $") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (qv != null && pv != null) onConfirm(qv, pv) }, enabled = qv != null && pv != null) { Text("تسجيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun trimPrice(v: Double): String = Engine.round2(v).toString().removeSuffix(".0")
private fun timeFmt(t: Long): String = SimpleDateFormat("d/M HH:mm", Locale.US).format(Date(t))
private fun dateFmt(t: Long): String = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(t))
