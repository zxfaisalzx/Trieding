package com.faisal.iltizam.work

import android.content.Context
import com.faisal.iltizam.data.PriceApi
import com.faisal.iltizam.data.Repository
import com.faisal.iltizam.data.Settings
import com.faisal.iltizam.domain.Alert
import com.faisal.iltizam.domain.Engine

object PriceUpdater {
    /** يجلب أسعار كل المراكز المفتوحة، يحدّث الوقف، ويرسل التنبيهات. يرجع رسالة خطأ إن وجدت. */
    suspend fun refreshAll(context: Context): String? {
        val repo = Repository.get(context)
        val key = Settings(context).apiKey
        val symbols = repo.positions.value.filter { !it.isClosed }.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return null

        val prices = mutableMapOf<String, Double>()
        var error: String? = null
        for (s in symbols) {
            try {
                prices[s] = PriceApi.quote(s, key)
            } catch (e: PriceApi.ApiException) {
                error = e.message
                if (key.isBlank()) break
            } catch (e: Exception) {
                error = "تعذّر الاتصال بمصدر الأسعار"
            }
        }

        val alerts = mutableListOf<Alert>()
        val now = System.currentTimeMillis()
        repo.update { list ->
            list.map { p ->
                val price = prices[p.symbol]
                if (price == null || p.isClosed) p
                else {
                    val (np, a) = Engine.onPrice(p, price, now)
                    alerts += a
                    np
                }
            }
        }
        alerts.forEach { Notifier.send(context, it) }
        return error
    }
}
