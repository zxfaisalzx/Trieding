package com.faisal.iltizam.domain

import kotlin.math.roundToLong

sealed class Alert {
    abstract val symbol: String

    data class TargetApproaching(override val symbol: String, val target: Tranche, val price: Double) : Alert()
    data class TargetHit(override val symbol: String, val target: Tranche, val price: Double) : Alert()
    data class StopHit(override val symbol: String, val stop: Tranche, val price: Double) : Alert()
    data class StopRaised(override val symbol: String, val oldPrice: Double, val newPrice: Double) : Alert()
}

/**
 * منطق الخطة: الوقف المتحرك الذي يضيق كلما اقترب السعر من الهدف، وتنبيهات الأهداف والوقف.
 * لا يعتمد على أندرويد حتى يمكن اختباره مباشرة.
 */
object Engine {
    /** التنبيه "اقترب من الهدف" عندما يكون السعر على بعد هذه النسبة أو أقل. */
    const val APPROACH_PCT = 0.02

    /** لا ننبهك على رفع الوقف إلا إذا ارتفع بنسبة 1% على الأقل عن آخر تنبيه. */
    const val STOP_RAISE_NOTIFY_PCT = 0.01

    /**
     * نسبة الوقف المتحرك تحت أعلى سعر:
     * - قبل نصف الطريق للهدف الأول: لا يتحرك (الوقف الذي حددته أنت)
     * - من 50% إلى 80% من الطريق: 15%
     * - من 80% حتى الهدف الأول: 10%
     * - بعد تنفيذ الهدف الأول: 8%
     * - بعد تنفيذ هدفين أو أكثر: 7%
     */
    fun trailPercent(p: Position): Double? {
        val done = p.targetsDone
        if (done >= 2) return 0.07
        if (done == 1) return 0.08
        val first = p.targets.filter { !it.done }.minByOrNull { it.price } ?: return null
        val range = first.price - p.avgCost
        if (range <= 0) return null
        val progress = (p.peakPrice - p.avgCost) / range
        return when {
            progress >= 0.8 - 1e-9 -> 0.10
            progress >= 0.5 - 1e-9 -> 0.15
            else -> null
        }
    }

    /** مستوى الوقف المتحرك المقترح لأعلى دفعة وقف، أو null إذا لم يبدأ التحرك بعد. */
    fun trailLevel(p: Position): Double? = trailPercent(p)?.let { round2(p.peakPrice * (1 - it)) }

    /**
     * يرفع دفعات الوقف المفتوحة حسب الوقف المتحرك. الوقف لا ينزل أبداً.
     * أعلى دفعة وقف تتبع المستوى المقترح، والباقي يحافظ على نفس النسبة بينه وبينها
     * (مثلاً نصف الكمية عند 30 والنصف الآخر عند 27 يبقى الفرق بينهما 10%).
     */
    fun applyTrailing(p: Position): Position {
        if (!p.smartTrailing) return p
        val level = trailLevel(p) ?: return p
        val open = p.stops.filter { !it.done }
        if (open.isEmpty()) return p
        val topOriginal = open.maxOf { it.originalPrice }
        if (topOriginal <= 0) return p
        val stops = p.stops.map { s ->
            if (s.done) s
            else {
                val candidate = round2(level * (s.originalPrice / topOriginal))
                if (candidate > s.price) s.copy(price = candidate, hitNotified = false) else s
            }
        }
        return p.copy(stops = stops)
    }

    /** يحدّث المركز بسعر جديد ويرجع التنبيهات التي يجب إرسالها. */
    fun onPrice(p0: Position, price: Double, now: Long): Pair<Position, List<Alert>> {
        val alerts = mutableListOf<Alert>()
        val oldTop = p0.highestStop
        var p = p0.copy(lastPrice = price, lastUpdated = now, peakPrice = maxOf(p0.peakPrice, price))
        if (p.isClosed) return p to emptyList()

        p = applyTrailing(p)
        val newTop = p.highestStop
        if (newTop != null && oldTop != null && newTop > oldTop) {
            val base = p.notifiedStopPrice ?: oldTop
            if (newTop >= base * (1 + STOP_RAISE_NOTIFY_PCT)) {
                alerts += Alert.StopRaised(p.symbol, base, newTop)
                p = p.copy(notifiedStopPrice = newTop)
            }
        }

        val targets = p.targets.map { t ->
            when {
                t.done -> t
                price >= t.price && !t.hitNotified -> {
                    alerts += Alert.TargetHit(p.symbol, t, price)
                    t.copy(hitNotified = true, approachNotified = true)
                }
                price < t.price && price >= t.price * (1 - APPROACH_PCT) && !t.approachNotified -> {
                    alerts += Alert.TargetApproaching(p.symbol, t, price)
                    t.copy(approachNotified = true)
                }
                else -> t
            }
        }
        val stops = p.stops.map { s ->
            if (!s.done && price <= s.price && !s.hitNotified) {
                alerts += Alert.StopHit(p.symbol, s, price)
                s.copy(hitNotified = true)
            } else s
        }
        return p.copy(targets = targets, stops = stops) to alerts
    }

    /** تسجيل تنفيذ دفعة (هدف أو وقف) بالسعر الفعلي. */
    fun executeTranche(p: Position, trancheId: String, price: Double, now: Long): Position {
        val inTargets = p.targets.any { it.id == trancheId }
        val tranche = (p.targets + p.stops).firstOrNull { it.id == trancheId && !it.done } ?: return p
        val qty = minOf(tranche.qty, p.remainingQty)
        if (qty <= 0) return p
        fun mark(list: List<Tranche>) = list.map {
            if (it.id == trancheId) it.copy(done = true, donePrice = price, doneAt = now) else it
        }
        var updated = p.copy(
            targets = if (inTargets) mark(p.targets) else p.targets,
            stops = if (!inTargets) mark(p.stops) else p.stops,
            sales = p.sales + Sale(qty, price, now, planned = true),
        )
        updated = applyTrailing(updated)
        return updated
    }

    /** بيع خارج الخطة. */
    fun freeSale(p: Position, qty: Int, price: Double, now: Long): Position {
        val q = minOf(qty, p.remainingQty)
        if (q <= 0) return p
        return p.copy(sales = p.sales + Sale(q, price, now, planned = false))
    }

    fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
}
