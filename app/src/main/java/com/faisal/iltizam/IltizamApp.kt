package com.faisal.iltizam

import android.app.Application
import com.faisal.iltizam.work.Notifier
import com.faisal.iltizam.work.PriceWorker

class IltizamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        PriceWorker.schedule(this)
    }
}
