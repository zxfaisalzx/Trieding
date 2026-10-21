package com.faisal.iltizam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faisal.iltizam.domain.Position

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    hasApiKey: Boolean,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    val positions by vm.positions.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showClosed by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    val open = positions.filter { !it.isClosed }
    val closed = positions.filter { it.isClosed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التزام", fontWeight = FontWeight.Bold) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "تحديث الأسعار") }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "الإعدادات") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("إضافة سهم") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasApiKey) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onSettings),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(
                            "عشان تتحدث الأسعار تلقائياً أضف مفتاح Finnhub المجاني من الإعدادات. اضغط هنا.",
                            Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item { SummaryCard(open, closed) }
            if (open.isEmpty()) {
                item {
                    Text(
                        "ما عندك أسهم. اضغط \"إضافة سهم\" وحط خطة البيع والوقف قبل لا تنسى.",
                        Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(open, key = { it.id }) { p -> PositionCard(p) { onOpen(p.id) } }
            if (closed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showClosed = !showClosed }) {
                        Text(if (showClosed) "إخفاء الصفقات المغلقة" else "الصفقات المغلقة (${closed.size})")
                    }
                }
                if (showClosed) items(closed, key = { it.id }) { p -> PositionCard(p) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun SummaryCard(open: List<Position>, closed: List<Position>) {
    val capital = open.sumOf { it.remainingQty * it.avgCost }
    val value = open.sumOf { it.marketValue }
    val unrealized = open.sumOf { it.unrealizedProfit }
    val realized = (open + closed).sumOf { it.realizedProfit }
    val locked = open.sumOf { it.lockedProfit }
    val neutral = MaterialTheme.colorScheme.onSurface

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("القيمة الحالية", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(money(value), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("رأس المال", money(capital), neutral, Modifier.weight(1f))
                Stat("ربح غير محقق", signedMoney(unrealized), pnlColor(unrealized, neutral), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("ربح محقق", signedMoney(realized), pnlColor(realized, neutral), Modifier.weight(1f))
                Stat(
                    if (locked >= 0) "ربح مؤمَّن بالوقف" else "أقصى خسارة لو ضرب الوقف",
                    signedMoney(locked), pnlColor(locked, neutral), Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun PositionCard(p: Position, onClick: () -> Unit) {
    val neutral = MaterialTheme.colorScheme.onSurface
    val price = p.lastPrice
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.symbol, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${qty(p.remainingQty)} سهم × ${money(p.avgCost)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(price?.let { money(it) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (p.isClosed) {
                        Text("مغلقة ${signedMoney(p.realizedProfit)}", color = pnlColor(p.realizedProfit, neutral), fontSize = 13.sp)
                    } else if (price != null) {
                        val ch = (price - p.avgCost) / p.avgCost
                        Text(
                            "${signedMoney(p.unrealizedProfit)} (${pct(ch)})",
                            color = pnlColor(p.unrealizedProfit, neutral), fontSize = 13.sp
                        )
                    }
                }
            }
            if (!p.isClosed) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("الهدف القادم", p.nextTarget?.let { "${money(it.price)} (${qty(it.qty)})" } ?: "—", neutral, Modifier.weight(1f))
                    Stat("الوقف", p.highestStop?.let { money(it) } ?: "بدون وقف", if (p.highestStop == null) Loss else neutral, Modifier.weight(1f))
                    Stat(
                        if (p.lockedProfit >= 0) "مؤمَّن" else "أقصى خسارة",
                        signedMoney(p.lockedProfit), pnlColor(p.lockedProfit, neutral), Modifier.weight(1f)
                    )
                }
                ProgressToTarget(p)
            }
        }
    }
}

/** شريط يوضح موقع السعر بين الوقف والهدف القادم. */
@Composable
private fun ProgressToTarget(p: Position) {
    val target = p.nextTarget?.price ?: return
    val price = p.lastPrice ?: return
    val low = p.highestStop ?: p.avgCost
    if (target <= low) return
    val f = ((price - low) / (target - low)).coerceIn(0.0, 1.0).toFloat()
    Box(Modifier.padding(top = 10.dp)) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}
