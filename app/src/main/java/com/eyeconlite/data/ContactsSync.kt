package com.eyeconlite.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract

data class DeviceContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val numbers: List<String>,
    val hasPhoto: Boolean
)

object ContactsSync {

    const val WORK_NAME = "contact-photo-sync"

    private const val PREFS = "eyecon_contacts_sync"
    private const val KEY_DONE = "sync_done_v1"
    private const val KEY_IDS = "synced_ids_v1"
    private const val KEY_UPDATED = "photos_updated_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All phonebook contacts that have at least one number. */
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

    /**
     * Permanently save [photoBytes] as the contact's profile photo.
     * Only the photo is written — the name is never changed.
     */
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

    /**
     * Create a new phonebook contact with name + number + photo.
     * Called with user-confirmed (customizable) values from the save dialog.
     */
    fun saveNewContact(
        context: Context,
        name: String,
        phone: String,
        photoBytes: ByteArray?
    ): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
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

    // ---- one-time scan bookkeeping (never auto-rescans) ----

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
