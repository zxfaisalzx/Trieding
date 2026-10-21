package com.faisal.iltizam.data

import android.content.Context
import com.faisal.iltizam.domain.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** يحفظ المراكز في ملف JSON على الجوال فقط. */
class Repository private constructor(context: Context) {
    private val file = File(context.filesDir, "positions.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _positions = MutableStateFlow(load())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private fun load(): List<Position> = try {
        if (file.exists()) json.decodeFromString<List<Position>>(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** يعيد قراءة الملف (مثلاً بعد ما يحدّثه العامل في الخلفية). */
    fun reload() {
        _positions.value = load()
    }

    suspend fun update(transform: (List<Position>) -> List<Position>) = withContext(Dispatchers.IO) { mutex.withLock {
        val current = load()
        val next = transform(current)
        val tmp = File(file.parentFile, "positions.json.tmp")
        tmp.writeText(json.encodeToString(next))
        tmp.renameTo(file)
        _positions.value = next
    } }

    suspend fun upsert(p: Position) = update { list ->
        if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
