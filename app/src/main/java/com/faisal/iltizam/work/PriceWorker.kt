package com.faisal.iltizam.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.faisal.iltizam.data.PriceApi
import com.faisal.iltizam.data.Settings
import java.util.concurrent.TimeUnit

class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        if (settings.onlyMarketHours && !PriceApi.isMarketOpen()) return Result.success()
        PriceUpdater.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "price-check"

        fun schedule(context: Context, replace: Boolean = false) {
            val minutes = Settings(context).intervalMinutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<PriceWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
