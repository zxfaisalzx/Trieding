package com.faisal.iltizam.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faisal.iltizam.data.PriceApi
import com.faisal.iltizam.data.Settings
import com.faisal.iltizam.work.PriceWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(settings.apiKey) }
    var interval by remember { mutableStateOf(settings.intervalMinutes) }
    var marketOnly by remember { mutableStateOf(settings.onlyMarketHours) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = {
                        settings.apiKey = key
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مفتاح الأسعار (Finnhub)", fontWeight = FontWeight.Bold)
                    Text(
                        "1) سجّل مجاناً في finnhub.io\n2) انسخ الـ API Key من لوحة التحكم\n3) الصقه هنا",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://finnhub.io/register")))
                    }) { Text("فتح موقع Finnhub") }
                    OutlinedTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true)
                    Button(onClick = {
                        settings.apiKey = key
                        testResult = "جاري التجربة..."
                        scope.launch {
                            testResult = try {
                                "يشتغل ✓ سعر AAPL الحين ${money(PriceApi.quote("AAPL", key))}"
                            } catch (e: Exception) {
                                e.message ?: "ما اشتغل"
                            }
                        }
                    }) { Text("حفظ وتجربة") }
                    testResult?.let { Text(it, fontSize = 13.sp) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الفحص في الخلفية", fontWeight = FontWeight.Bold)
                    Text(
                        "أندرويد ما يسمح بأقل من 15 دقيقة، وأحياناً يتأخر لتوفير البطارية. عشان كذا خلك دائماً حاط أمر الوقف عند الوسيط.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            FilterChip(
                                selected = interval == m,
                                onClick = {
                                    interval = m
                                    settings.intervalMinutes = m
                                    PriceWorker.schedule(context, replace = true)
                                },
                                label = { Text("كل $m دقيقة") }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("أوقات السوق الرسمية فقط", Modifier.weight(1f))
                        Switch(marketOnly, {
                            marketOnly = it
                            settings.onlyMarketHours = it
                        })
                    }
                    Text(
                        "السوق الأمريكي بتوقيت السعودية: 4:30 العصر إلى 11 الليل، و5:30 إلى 12 في الشتاء.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Text(
                    "التطبيق أداة تساعدك تلتزم بقواعدك أنت، وليس نصيحة مالية. البيانات محفوظة على جوالك فقط.",
                    Modifier.padding(16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
