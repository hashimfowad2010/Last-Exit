package com.lastexit.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.lastexit.app.data.LastExitDatabase
import com.lastexit.app.data.TrackerStore
import com.lastexit.app.notify.LimitCheckScheduler
import com.lastexit.app.notify.Notifier
import com.lastexit.app.notify.StatusMonitor
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LastExitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.notifier.ensureChannels()
        LimitCheckScheduler.schedule(this)
        graph.store.whenLoaded { }
    }
}

/** Hand-rolled dependency container: one instance of each collaborator for the whole process. */
class AppGraph(context: Context) {
    val main = Handler(Looper.getMainLooper())
    val io: ExecutorService = Executors.newSingleThreadExecutor()
    val store = TrackerStore(LastExitDatabase(context), io, main)
    val notifier = Notifier(context)
    val monitor = StatusMonitor(store, notifier)
    val prefs: SharedPreferences = context.getSharedPreferences("last_exit_prefs", Context.MODE_PRIVATE)

    /** The real date. Only Demo mode uses a different "today", and it never goes through here. */
    fun today(): LocalDate = LocalDate.now()
}

val Context.graph: AppGraph get() = (applicationContext as LastExitApp).graph
