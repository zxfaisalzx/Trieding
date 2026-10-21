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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faisal.iltizam.domain.Position
import com.faisal.iltizam.domain.Tranche
import java.util.UUID

/** صف في النموذج: دفعة بيع أو وقف. الدفعات المنفّذة تظهر ولا تتعدل. */
private data class FormRow(
    val id: String = UUID.randomUUID().toString(),
    val price: String = "",
    val qty: String = "",
    val done: Tranche? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(existing: Position?, onSave: (Position) -> Unit, onBack: () -> Unit) {
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var qtyText by remember { mutableStateOf(existing?.qty?.toString() ?: "") }
    var avgText by remember { mutableStateOf(existing?.avgCost?.let { trimNum(it) } ?: "") }
    var smart by remember { mutableStateOf(existing?.smartTrailing ?: true) }
    val targets = remember {
        mutableStateListOf<FormRow>().apply {
            existing?.targets?.sortedBy { it.price }?.forEach { add(it.toRow()) }
            if (isEmpty()) add(FormRow())
        }
    }
    val stops = remember {
        mutableStateListOf<FormRow>().apply {
            existing?.stops?.sortedByDescending { it.price }?.forEach { add(it.toRow()) }
            if (isEmpty()) add(FormRow())
        }
    }

    val total = parseInt(qtyText) ?: 0
    val avg = parseNum(avgText) ?: 0.0
    val targetSum = targets.sumOf { parseInt(it.qty) ?: 0 }
    val stopSum = stops.sumOf { parseInt(it.qty) ?: 0 }

    val errors = buildList {
        if (symbol.isBlank()) add("اكتب رمز السهم")
        if (total <= 0) add("اكتب الكمية")
        if (avg <= 0) add("اكتب سعر الشراء")
        if (targetSum > total) add("مجموع كميات البيع (${qty(targetSum)}) أكبر من كميتك (${qty(total)})")
        if (stopSum > total) add("مجموع كميات الوقف (${qty(stopSum)}) أكبر من كميتك (${qty(total)})")
        val badRow = (targets + stops).any { r ->
            r.done == null && (r.price.isNotBlank() || r.qty.isNotBlank()) &&
                ((parseNum(r.price) ?: 0.0) <= 0 || (parseInt(r.qty) ?: 0) <= 0)
        }
        if (badRow) add("كل دفعة تحتاج سعر وكمية")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "سهم جديد" else "تعديل ${existing.symbol}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                symbol, { symbol = it.uppercase().trim() }, Modifier.fillMaxWidth(),
                label = { Text("رمز السهم (مثل IONQ)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    qtyText, { qtyText = it }, Modifier.weight(1f), label = { Text("الكمية") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    avgText, { avgText = it }, Modifier.weight(1f), label = { Text("متوسط سعر الشراء $") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (total > 0 && avg > 0) {
                Text("رأس المال: ${money(total * avg)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TrancheSection(
                title = "دفعات البيع (الأهداف)",
                hint = "متى تبيع وكم. مثال: 50 سهم عند 40، و50 عند 42.",
                rows = targets, total = total, avg = avg, isStop = false,
            )
            TrancheSection(
                title = "دفعات الوقف",
                hint = "إذا نزل السعر لهنا تبيع. تقدر تقسم: نص الكمية عند سعر والنص الثاني أقل.",
                rows = stops, total = total, avg = avg, isStop = true,
            )

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("وقف متحرك ذكي", fontWeight = FontWeight.SemiBold)
                        Text(
                            "يرفع الوقف تلقائياً ويضيّقه كلما قرب السعر من الهدف: 15% تحت أعلى سعر بعد نص الطريق، 10% قرب الهدف، 8% بعد أول هدف، 7% بعد الثاني. الوقف ما ينزل أبداً.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(smart, { smart = it })
                }
            }

            errors.forEach { Text("• $it", color = Loss, fontSize = 13.sp) }

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val p = (existing ?: Position(UUID.randomUUID().toString(), symbol, total, avg, now)).copy(
                        symbol = symbol,
                        qty = total,
                        avgCost = avg,
                        smartTrailing = smart,
                        peakPrice = maxOf(existing?.peakPrice ?: avg, avg),
                        targets = targets.toTranches(existing?.targets),
                        stops = stops.toTranches(existing?.stops),
                    )
                    onSave(p)
                },
                enabled = errors.isEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("حفظ الخطة", fontSize = 16.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrancheSection(
    title: String,
    hint: String,
    rows: SnapshotStateList<FormRow>,
    total: Int,
    avg: Double,
    isStop: Boolean,
) {
    val used = rows.sumOf { parseInt(it.qty) ?: 0 }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider()
                if (r.done != null) {
                    Text(
                        "✓ منفّذة: ${qty(r.done.qty)} سهم عند ${money(r.done.donePrice ?: r.done.price)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TrancheRow(r, total, used, avg, isStop,
                        onChange = { rows[i] = it },
                        onRemove = { rows.removeAt(i) })
                }
            }
            OutlinedButton(onClick = { rows.add(FormRow()) }) {
                Icon(Icons.Default.Add, null)
                Text(if (isStop) "إضافة دفعة وقف" else "إضافة دفعة بيع")
            }
            if (total > 0) {
                val left = total - used
                Text(
                    if (left > 0) "باقي ${qty(left)} سهم بدون ${if (isStop) "وقف" else "هدف"}"
                    else "كل الكمية مغطاة",
                    fontSize = 12.sp,
                    color = if (left < 0) Loss else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrancheRow(r: FormRow, total: Int, used: Int, avg: Double, isStop: Boolean, onChange: (FormRow) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                r.price, { onChange(r.copy(price = it)) }, Modifier.weight(1f),
                label = { Text("السعر $") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                r.qty, { onChange(r.copy(qty = it)) }, Modifier.weight(1f),
                label = { Text("الكمية") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "حذف") }
        }
        if (total > 0) {
            val mine = parseInt(r.qty) ?: 0
            val free = (total - (used - mine)).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onChange(r.copy(qty = (total / 4).toString())) }, label = { Text("ربع") })
                AssistChip(onClick = { onChange(r.copy(qty = (total / 2).toString())) }, label = { Text("نص") })
                AssistChip(onClick = { onChange(r.copy(qty = free.toString())) }, label = { Text("الباقي") })
            }
        }
        val price = parseNum(r.price)
        val q = parseInt(r.qty)
        if (price != null && q != null && q > 0 && avg > 0) {
            val pl = (price - avg) * q
            val ch = (price - avg) / avg
            Text(
                (if (pl >= 0) "ربح " else "خسارة ") + "${money(kotlin.math.abs(pl))} (${pct(ch)})",
                fontSize = 12.sp, color = pnlColor(pl, MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

private fun Tranche.toRow() = if (done) FormRow(id = id, done = this) else FormRow(id = id, price = trimNum(price), qty = qty.toString())

private fun List<FormRow>.toTranches(old: List<Tranche>?): List<Tranche> = mapNotNull { r ->
    if (r.done != null) return@mapNotNull r.done
    val price = parseNum(r.price) ?: return@mapNotNull null
    val q = parseInt(r.qty) ?: return@mapNotNull null
    if (price <= 0 || q <= 0) return@mapNotNull null
    val prev = old?.firstOrNull { it.id == r.id }
    if (prev != null && prev.price == price && prev.qty == q) prev
    else Tranche(id = r.id, price = price, qty = q, originalPrice = price)
}

private fun trimNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
