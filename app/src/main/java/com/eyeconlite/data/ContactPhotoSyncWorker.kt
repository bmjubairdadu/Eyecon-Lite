package com.eyeconlite.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

class ContactPhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
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
