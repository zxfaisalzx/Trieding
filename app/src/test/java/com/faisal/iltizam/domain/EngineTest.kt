package com.faisal.iltizam.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun faisalTrade() = Position(
        id = "p", symbol = "TEST", qty = 200, avgCost = 29.0, createdAt = 0,
        targets = listOf(Tranche("t1", 40.0, 50), Tranche("t2", 42.0, 50)),
        stops = listOf(Tranche("s1", 25.0, 200)),
    )

    /** صفقة فيصل السابقة: 200 سهم بـ 29، بيع 50 عند 40 و50 عند 42، القمة 45. */
    @Test
    fun faisalPastTradeLocksAbout2485() {
        var p = faisalTrade()
        fun tick(price: Double): List<Alert> {
            val (np, alerts) = Engine.onPrice(p, price, 0)
            p = np
            return alerts
        }
        tick(34.5)
        assertEquals(29.33, p.highestStop!!, 0.011)
        tick(37.8)
        assertEquals(34.02, p.highestStop!!, 0.001)
        assertTrue(tick(40.0).any { it is Alert.TargetHit })
        p = Engine.executeTranche(p, "t1", 40.0, 0)
        assertEquals(36.8, p.highestStop!!, 0.001)
        tick(42.0)
        p = Engine.executeTranche(p, "t2", 42.0, 0)
        assertEquals(39.06, p.highestStop!!, 0.001)
        tick(45.0)
        assertEquals(41.85, p.highestStop!!, 0.001)
        assertEquals(2485.0, p.lockedProfit, 0.01)
        assertTrue(tick(41.85).any { it is Alert.StopHit })
        p = Engine.executeTranche(p, "s1", 41.85, 0)
        assertEquals(0, p.remainingQty)
        assertEquals(2485.0, p.realizedProfit, 0.01)
    }

    @Test
    fun stopNeverMovesDown() {
        var p = faisalTrade()
        p = Engine.onPrice(p, 38.0, 0).first
        val high = p.highestStop!!
        p = Engine.onPrice(p, 33.0, 0).first
        assertEquals(high, p.highestStop!!, 0.001)
    }

    @Test
    fun splitStopsKeepTheirGap() {
        var p = faisalTrade().copy(stops = listOf(Tranche("a", 27.0, 100), Tranche("b", 24.3, 100)))
        p = Engine.onPrice(p, 40.0, 0).first // 10% تحت 40 = 36
        assertEquals(36.0, p.stops.first { it.id == "a" }.price, 0.001)
        assertEquals(32.4, p.stops.first { it.id == "b" }.price, 0.001)
    }

    @Test
    fun alertsFireOnce() {
        var p = faisalTrade()
        val (p1, a1) = Engine.onPrice(p, 39.5, 0)
        assertTrue(a1.any { it is Alert.TargetApproaching })
        val (_, a2) = Engine.onPrice(p1, 39.6, 0)
        assertTrue(a2.none { it is Alert.TargetApproaching })
    }

    @Test
    fun noTrailingWhenDisabled() {
        var p = faisalTrade().copy(smartTrailing = false)
        p = Engine.onPrice(p, 45.0, 0).first
        assertEquals(25.0, p.highestStop!!, 0.001)
    }
}
