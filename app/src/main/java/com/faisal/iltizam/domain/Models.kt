package com.faisal.iltizam.domain

import kotlinx.serialization.Serializable

/** دفعة بيع عند هدف، أو دفعة وقف خسارة/حماية ربح. */
@Serializable
data class Tranche(
    val id: String,
    /** السعر الذي حدّدته أنت. للوقف قد يرفعه الوقف المتحرك تلقائياً. */
    val price: Double,
    val qty: Int,
    /** السعر الأصلي عند إنشاء الخطة (للوقف المتحرك يحفظ الفارق بين دفعات الوقف). */
    val originalPrice: Double = price,
    val done: Boolean = false,
    val donePrice: Double? = null,
    val doneAt: Long? = null,
    val approachNotified: Boolean = false,
    val hitNotified: Boolean = false,
)

@Serializable
data class Sale(
    val qty: Int,
    val price: Double,
    val at: Long,
    /** هل كان البيع حسب الخطة (هدف أو وقف) أو بيع حر. */
    val planned: Boolean,
)

@Serializable
data class Position(
    val id: String,
    val symbol: String,
    val qty: Int,
    val avgCost: Double,
    val createdAt: Long,
    val targets: List<Tranche> = emptyList(),
    val stops: List<Tranche> = emptyList(),
    /** وقف متحرك ذكي: يرفع الوقف تلقائياً ويضيّقه كلما اقترب السعر من الهدف. */
    val smartTrailing: Boolean = true,
    val sales: List<Sale> = emptyList(),
    val lastPrice: Double? = null,
    val lastUpdated: Long? = null,
    /** أعلى سعر شوهد منذ الشراء. */
    val peakPrice: Double = avgCost,
    /** آخر سعر وقف أرسلنا لك تنبيه بتحديثه عند الوسيط. */
    val notifiedStopPrice: Double? = null,
) {
    val soldQty: Int get() = sales.sumOf { it.qty }
    val remainingQty: Int get() = (qty - soldQty).coerceAtLeast(0)
    val capital: Double get() = qty * avgCost
    val realizedProfit: Double get() = sales.sumOf { (it.price - avgCost) * it.qty }
    val marketValue: Double get() = remainingQty * (lastPrice ?: avgCost)
    val unrealizedProfit: Double get() = remainingQty * ((lastPrice ?: avgCost) - avgCost)
    val totalProfit: Double get() = realizedProfit + unrealizedProfit
    val isClosed: Boolean get() = remainingQty == 0

    val openTargets: List<Tranche> get() = targets.filter { !it.done }.sortedBy { it.price }
    val openStops: List<Tranche> get() = stops.filter { !it.done }.sortedByDescending { it.price }
    val nextTarget: Tranche? get() = openTargets.firstOrNull()
    val highestStop: Double? get() = openStops.maxOfOrNull { it.price }

    /**
     * الربح المؤمَّن: الربح المحقق + ما تضمنه أوامر الوقف للكمية المتبقية.
     * الكمية المتبقية التي لا يغطيها أي وقف تُحسب على أنها غير مؤمّنة (صفر، وليست خسارة).
     */
    val lockedProfit: Double
        get() {
            var covered = 0
            var locked = realizedProfit
            for (s in openStops) {
                val q = minOf(s.qty, remainingQty - covered)
                if (q <= 0) break
                locked += (s.price - avgCost) * q
                covered += q
            }
            return locked
        }

    /** عدد أهداف البيع المنفّذة. */
    val targetsDone: Int get() = targets.count { it.done }
}
