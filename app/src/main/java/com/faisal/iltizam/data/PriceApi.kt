package com.faisal.iltizam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** أسعار الأسهم الأمريكية من Finnhub (مجاني بمفتاح). */
object PriceApi {
    class ApiException(message: String) : Exception(message)

    suspend fun quote(symbol: String, apiKey: String): Double = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ApiException("أضف مفتاح Finnhub من الإعدادات")
        val s = URLEncoder.encode(symbol.uppercase(), "UTF-8")
        val k = URLEncoder.encode(apiKey, "UTF-8")
        val conn = URL("https://finnhub.io/api/v1/quote?symbol=$s&token=$k").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            when (conn.responseCode) {
                200 -> {}
                401, 403 -> throw ApiException("مفتاح Finnhub غير صحيح")
                429 -> throw ApiException("تجاوزت حد الطلبات، حاول بعد دقيقة")
                else -> throw ApiException("خطأ من مصدر الأسعار (${conn.responseCode})")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val price = JSONObject(body).optDouble("c", 0.0)
            if (price <= 0.0) throw ApiException("ما لقيت سعر للرمز $symbol")
            price
        } finally {
            conn.disconnect()
        }
    }

    /** السوق الأمريكي الرسمي: الإثنين إلى الجمعة 9:30 إلى 16:00 بتوقيت نيويورك (بدون العطل). */
    fun isMarketOpen(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York"))): Boolean {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = now.toLocalTime()
        return !t.isBefore(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(16, 5))
    }
}
