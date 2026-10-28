package bd.app.aismartreply

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class DriveBackupJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0
    override fun onStartJob(params: JobParameters): Boolean {
        val ticket = ++generation
        executor.execute {
            val retry = DriveBackups.runJob(applicationContext)
            Handler(Looper.getMainLooper()).post { if (ticket == generation) jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { generation++; return true }
    override fun onDestroy() { generation++; executor.shutdownNow(); super.onDestroy() }
}
