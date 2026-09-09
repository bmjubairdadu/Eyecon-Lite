package com.eyeconlite.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

class ContactPhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "eyecon_contact_scan"
        private const val NOTIFICATION_ID = 1001
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Contact scan",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Shows live status while Eyecon Lite scans contact photos."
        manager?.createNotificationChannel(channel)
    }

    private fun buildForegroundInfo(progress: Int, total: Int, title: String, text: String): ForegroundInfo {
        val max = if (total > 0) total else 100
        val percent = if (total > 0) progress.coerceIn(0, total) else 0
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        if (total > 0) {
            builder.setProgress(max, percent, false)
        } else {
            builder.setProgress(100, 0, true)
        }
        return ForegroundInfo(NOTIFICATION_ID, builder.build())
    }

    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        setForeground(buildForegroundInfo(0, 0, "Eyecon Lite", "Preparing contact scan…"))

        val force = inputData.getBoolean("force", false)
        if (!force && ContactsSync.isDone(applicationContext)) {
            return Result.success()
        }

        val contacts = try {
            ContactsSync.readContacts(applicationContext)
        } catch (_: Exception) {
            return Result.retry()
        }

        val pending = contacts.filter { c ->
            c.numbers.isNotEmpty() &&
                !c.hasPhoto &&
                !ContactsSync.isContactSynced(applicationContext, c.id)
        }
        val total = pending.sumOf { it.numbers.distinct().size }
        var done = 0
        var updated = ContactsSync.getUpdatedCount(applicationContext)
        val events = ArrayDeque<String>()

        suspend fun publish(number: String, name: String, status: String) {
            events.addFirst("$name | $number | $status")
            while (events.size > 12) events.removeLast()
            setForeground(buildForegroundInfo(done.coerceAtMost(total), total, "Scanning contacts", "$status • $name • $number"))
            setProgress(
                workDataOf(
                    "total" to total,
                    "done" to done,
                    "updated" to updated,
                    "currentNumber" to number,
                    "currentName" to name,
                    "currentStatus" to status,
                    "events" to events.joinToString("\n")
                )
            )
        }

        for (c in pending) {
            if (isStopped) return Result.retry() // synced ids persist; continues later
            var saved = false
            for (num in c.numbers.distinct()) {
                publish(num, c.name, "Scanning")
                try {
                    val bytes = EyeconApi.fetchPhotoForNumber(num)
                    if (bytes == null) {
                        publish(num, c.name, "No photo found")
                    } else if (!saved && ContactsSync.setContactPhoto(applicationContext, c.id, bytes)) {
                        saved = true
                        updated++
                        publish(num, c.name, "Photo saved")
                    } else {
                        publish(num, c.name, "Photo found")
                    }
                } catch (_: Exception) {
                    publish(num, c.name, "Lookup failed")
                }
                done++
                delay(350)
            }
            ContactsSync.markContactSynced(applicationContext, c.id, saved)
        }

        ContactsSync.setDone(applicationContext, true)
        setForeground(buildForegroundInfo(total, total, "Contact scan complete", "Photos synced: $updated"))
        setProgress(
            workDataOf(
                "total" to total,
                "done" to done,
                "updated" to updated,
                "currentStatus" to "Scan complete",
                "events" to events.joinToString("\n")
            )
        )
        return Result.success(workDataOf("total" to total, "done" to done, "updated" to updated))
    }
}
