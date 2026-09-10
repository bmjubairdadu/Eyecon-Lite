package com.eyeconlite.data

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.TelephonyManager

data class DeviceContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val numbers: List<String>,
    val hasPhoto: Boolean
)

data class SavedContactMeta(
    val device: String,
    val sim: String,
    val email: String,
    val savedAt: Long
)

data class SaveDestination(
    val accountType: String?,
    val accountName: String?,
    val label: String,
    val detail: String,
    val sim: Boolean = false
)

object ContactsSync {

    const val WORK_NAME = "contact-photo-sync"

    private const val PREFS = "eyecon_contacts_sync"
    private const val KEY_DONE = "sync_done_v1"
    private const val KEY_IDS = "synced_ids_v1"
    private const val KEY_UPDATED = "photos_updated_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun buildSavedMeta(context: Context, email: String? = null): SavedContactMeta {
        val device = "${Build.MANUFACTURER ?: "Unknown"} ${Build.MODEL ?: "Device"}".trim()
        val sim = runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            listOfNotNull(
                tm?.simOperatorName,
                tm?.networkOperatorName
            ).firstOrNull { it.isNotBlank() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unavailable"
        return SavedContactMeta(
            device = device.ifBlank { "Unknown device" },
            sim = sim.ifBlank { "Unavailable" },
            email = email?.trim().orEmpty().ifBlank { "Not provided" },
            savedAt = System.currentTimeMillis()
        )
    }

    fun simName(context: Context): String {
        return runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            listOfNotNull(
                tm?.simOperatorName,
                tm?.networkOperatorName
            ).firstOrNull { it.isNotBlank() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SIM card"
    }

    /** All places the contact can be saved: phone, synced accounts (Google/mail), SIM. */
    fun listSaveDestinations(context: Context): List<SaveDestination> {
        val out = ArrayList<SaveDestination>()
        val device = "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim()
        out.add(
            SaveDestination(
                accountType = null,
                accountName = null,
                label = "Phone (This device)",
                detail = device.ifBlank { "Local contacts" }
            )
        )
        val seen = HashSet<String>()
        // Existing contact accounts — needs only READ_CONTACTS.
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0)
                    val name = c.getString(1)
                    if (type.isNullOrBlank() || name.isNullOrBlank()) continue
                    val key = "$type|$name"
                    if (!seen.add(key)) continue
                    out.add(
                        SaveDestination(
                            accountType = type,
                            accountName = name,
                            label = if (type.contains("google", ignoreCase = true)) "Google" else name,
                            detail = name
                        )
                    )
                }
            }
        }
        // Google accounts with no contacts yet — best effort, may need GET_ACCOUNTS.
        runCatching {
            val am = AccountManager.get(context)
            for (a in am.getAccountsByType("com.google")) {
                val key = "com.google|${a.name}"
                if (!seen.add(key)) continue
                out.add(
                    SaveDestination(
                        accountType = a.type,
                        accountName = a.name,
                        label = "Google",
                        detail = a.name
                    )
                )
            }
        }
        out.add(
            SaveDestination(
                accountType = "sim",
                accountName = "sim",
                label = "SIM card",
                detail = "${simName(context)} • name + number only",
                sim = true
            )
        )
        return out
    }

    fun formatSavedSummary(context: Context, email: String? = null): String {
        val meta = buildSavedMeta(context, email)
        return "Device: ${meta.device} • SIM: ${meta.sim} • Email: ${meta.email}"
    }

    fun readContacts(context: Context): List<DeviceContact> {
        val cr = context.contentResolver
        val out = ArrayList<DeviceContact>()
        val cur = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_ID
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        ) ?: return out
        cur.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_ID)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val numbers = readNumbersFor(cr, id)
                if (numbers.isEmpty()) continue
                val photoId = if (c.isNull(photoIdx)) 0L else c.getLong(photoIdx)
                out.add(
                    DeviceContact(
                        id = id,
                        lookupKey = c.getString(keyIdx) ?: "",
                        name = c.getString(nameIdx) ?: "Unknown",
                        numbers = numbers,
                        hasPhoto = photoId != 0L
                    )
                )
            }
        }
        return out
    }

    private fun readNumbersFor(cr: ContentResolver, contactId: Long): List<String> {
        val nums = ArrayList<String>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0)?.trim().orEmpty()
                if (n.filter { it.isDigit() }.length >= 7) nums.add(n)
            }
        }
        return nums.distinct()
    }

    fun setContactPhoto(context: Context, contactId: Long, photoBytes: ByteArray): Boolean {
        return try {
            val cr = context.contentResolver
            val rawIds = ArrayList<Long>()
            cr.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                ContactsContract.RawContacts.CONTACT_ID + "=?",
                arrayOf(contactId.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) rawIds.add(c.getLong(0))
            }
            if (rawIds.isEmpty()) return false
            val rawId = rawIds.first()

            var dataId: Long? = null
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                    ContactsContract.Data.MIMETYPE + "=?",
                arrayOf(
                    rawId.toString(),
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { c ->
                if (c.moveToFirst()) dataId = c.getLong(0)
            }

            val ops = ArrayList<ContentProviderOperation>()
            if (dataId != null) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            ContactsContract.Data._ID + "=?",
                            arrayOf(dataId.toString())
                        )
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }
            cr.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveNewContact(
        context: Context,
        name: String,
        phone: String,
        photoBytes: ByteArray?,
        email: String? = null,
        destination: SaveDestination? = null
    ): Boolean {
        if (destination?.sim == true) return saveToSim(context, name, phone)
        return try {
            val cleanEmail = email?.trim().orEmpty()
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, destination?.accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, destination?.accountName)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
            if (cleanEmail.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, cleanEmail)
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_HOME
                        )
                        .build()
                )
            }
            if (photoBytes != null && photoBytes.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** SIM cards only store name + number, so email/photo are skipped there. */
    private fun saveToSim(context: Context, name: String, phone: String): Boolean {
        val cleanName = name.trim().take(40)
        val cleanPhone = phone.filter { it.isDigit() || it == '+' }.trim()
        if (cleanName.isEmpty() || cleanPhone.filter { it.isDigit() }.length < 7) return false
        val uris = listOf("content://icc/adn", "content://sim/adn")
        for (base in uris) {
            try {
                val values = ContentValues().apply {
                    put("tag", cleanName)
                    put("number", cleanPhone)
                    put("name", cleanName)
                }
                val inserted = context.contentResolver.insert(Uri.parse(base), values)
                if (inserted != null) return true
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    fun isDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DONE, false)

    fun setDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_DONE, done).apply()
    }

    fun isContactSynced(context: Context, contactId: Long): Boolean =
        prefs(context).getStringSet(KEY_IDS, emptySet())?.contains(contactId.toString()) == true

    fun markContactSynced(context: Context, contactId: Long, photoUpdated: Boolean) {
        val p = prefs(context)
        val set = HashSet(p.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
        set.add(contactId.toString())
        p.edit().putStringSet(KEY_IDS, set).apply()
        if (photoUpdated) {
            p.edit().putInt(KEY_UPDATED, getUpdatedCount(context) + 1).apply()
        }
    }

    fun getUpdatedCount(context: Context): Int =
        prefs(context).getInt(KEY_UPDATED, 0)
}
