package com.lastexit.app.notify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.lastexit.app.graph
import java.util.concurrent.TimeUnit

/**
 * Periodic background re-evaluation of every tracker. Runs even when the app is closed, so a
 * status that worsens just because time passed still produces a warning.
 */
class LimitCheckJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val graph = applicationContext.graph
        graph.store.whenLoaded {
            graph.monitor.runBackgroundCheck(graph.today())
            graph.store.afterPendingWrites { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

object LimitCheckScheduler {
    private const val JOB_ID = 4_201
    private val INTERVAL_MS = TimeUnit.HOURS.toMillis(3)
    private val FLEX_MS = TimeUnit.HOURS.toMillis(1)

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, LimitCheckJobService::class.java))
            .setPeriodic(INTERVAL_MS, FLEX_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}
