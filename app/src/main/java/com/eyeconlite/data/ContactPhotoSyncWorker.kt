package com.eyeconlite.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

/**
 * Background scan: for every phonebook contact that has NO profile photo yet,
 * fetch only the photo and save it permanently on the contact.
 * Names are never touched. Already-processed contacts are skipped forever,
 * so reopening the app does not rescan. Runs even when the app is closed
 * (WorkManager, requires internet via constraints set at enqueue time).
 */
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
        val total = pending.size
        var done = 0
        var updated = ContactsSync.getUpdatedCount(applicationContext)
        setProgress(workDataOf("total" to total, "done" to 0, "updated" to updated))

        for (c in pending) {
            if (isStopped) return Result.retry() // synced ids persist; continues later
            var saved = false
            for (num in c.numbers.distinct()) {
                try {
                    val bytes = EyeconApi.fetchPhotoForNumber(num)
                    if (bytes != null &&
                        ContactsSync.setContactPhoto(applicationContext, c.id, bytes)
                    ) {
                        saved = true
                        updated++
                        break
                    }
                } catch (_: Exception) {
                    // try next number of the same contact
                }
            }
            ContactsSync.markContactSynced(applicationContext, c.id, saved)
            done++
            if (done % 2 == 0 || done == total) {
                setProgress(workDataOf("total" to total, "done" to done, "updated" to updated))
            }
            delay(350) // be polite to the API
        }

        ContactsSync.setDone(applicationContext, true)
        setProgress(workDataOf("total" to total, "done" to done, "updated" to updated))
        return Result.success(workDataOf("total" to total, "done" to done, "updated" to updated))
    }
}
