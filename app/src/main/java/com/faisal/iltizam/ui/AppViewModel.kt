package com.faisal.iltizam.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faisal.iltizam.data.Repository
import com.faisal.iltizam.domain.Engine
import com.faisal.iltizam.domain.Position
import com.faisal.iltizam.work.Notifier
import com.faisal.iltizam.work.PriceUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    val positions: StateFlow<List<Position>> = repo.positions

    val refreshing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun reload() = repo.reload()

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val err = PriceUpdater.refreshAll(getApplication())
            refreshing.value = false
            message.value = err
        }
    }

    fun save(p: Position) = viewModelScope.launch {
        // تطبيق الوقف المتحرك فوراً على الخطة الجديدة أو المعدّلة
        repo.upsert(Engine.applyTrailing(p))
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun execute(positionId: String, trancheId: String, price: Double) = viewModelScope.launch {
        repo.update { list ->
            list.map { if (it.id == positionId) Engine.executeTranche(it, trancheId, price, System.currentTimeMillis()) else it }
        }
    }

    fun freeSale(positionId: String, qty: Int, price: Double) = viewModelScope.launch {
        repo.update { list ->
            list.map { if (it.id == positionId) Engine.freeSale(it, qty, price, System.currentTimeMillis()) else it }
        }
    }

    /** إدخال السعر يدوياً (إذا ما عندك مفتاح أسعار أو تبي تجرّب). */
    fun manualPrice(positionId: String, price: Double) = viewModelScope.launch {
        val alerts = mutableListOf<com.faisal.iltizam.domain.Alert>()
        repo.update { list ->
            list.map {
                if (it.id == positionId) {
                    val (np, a) = Engine.onPrice(it, price, System.currentTimeMillis())
                    alerts += a
                    np
                } else it
            }
        }
        alerts.forEach { Notifier.send(getApplication(), it) }
    }
}
