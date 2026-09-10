package com.eyeconlite.data

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class DeviceContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val numbers: List<String>,
    val hasPhoto: Boolean
)

data class SaveDestination(
    val kind: String, // phone | account | sim
    val accountType: String?,
    val accountName: String?,
    val simSlot: Int = -1,
    val simSubId: Int = -1,
    val label: String,
    val detail: String
) {
    val isSim: Boolean get() = kind == "sim"
    fun key(): String = "$kind|${accountType.orEmpty()}|${accountName.orEmpty()}|$simSlot|$simSubId"
}

object ContactsSync {

    const val WORK_NAME = "contact-photo-sync"

    private const val PREFS = "eyecon_contacts_sync"
    private const val KEY_DONE = "sync_done_v1"
    private const val KEY_IDS = "synced_ids_v1"
    private const val KEY_UPDATED = "photos_updated_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun simName(context: Context): String {
        return runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            listOfNotNull(
                tm?.simOperatorName,
                tm?.networkOperatorName
            ).firstOrNull { it.isNotBlank() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SIM card"
    }

    /** Save locations: phone, every logged-in mail account, every SIM slot. */
    fun listSaveDestinations(context: Context): List<SaveDestination> {
        val out = ArrayList<SaveDestination>()
        val device = "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim()
        out.add(
            SaveDestination(
                kind = "phone",
                accountType = null,
                accountName = null,
                label = "Phone (This device)",
                detail = device.ifBlank { "Local contacts" }
            )
        )
        val seen = HashSet<String>().also { it.add(out.first().key()) }
        // Every mail-type account on the device (Google + others), one row each.
        for (a in mailAccounts(context)) {
            val d = SaveDestination(
                kind = "account",
                accountType = a.first,
                accountName = a.second,
                label = a.second,
                detail = accountDetailLabel(a.first)
            )
            if (seen.add(d.key())) out.add(d)
        }
        // Every SIM slot separately: SIM 1, SIM 2, ...
        val sims = simSlots(context)
        if (sims.isEmpty()) {
            val d = SaveDestination(
                kind = "sim",
                accountType = "sim",
                accountName = "sim",
                simSlot = 0,
                simSubId = 0,
                label = "SIM card",
                detail = "${simName(context)} • name + number only"
            )
            if (seen.add(d.key())) out.add(d)
        } else {
            for (sv in sims) {
                val d = SaveDestination(
                    kind = "sim",
                    accountType = "sim",
                    accountName = "sim${sv.slot + 1}",
                    simSlot = sv.slot,
                    simSubId = sv.subId,
                    label = sv.label,
                    detail = "${sv.detail} • name + number only"
                )
                if (seen.add(d.key())) out.add(d)
            }
        }
        return out
    }

    private fun accountDetailLabel(type: String): String = when {
        type.contains("google", ignoreCase = true) -> "Google account"
        type.contains("exchange", ignoreCase = true) -> "Exchange account"
        type.contains("outlook", ignoreCase = true) ||
            type.contains("hotmail", ignoreCase = true) -> "Outlook account"
        type.contains("yahoo", ignoreCase = true) -> "Yahoo account"
        else -> "Mail account"
    }

    /** (type, name) for every mail-capable account: existing contact accounts + device accounts. */
    private fun mailAccounts(context: Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        // Accounts already holding contacts — needs only READ_CONTACTS.
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
                    val type = c.getString(0).orEmpty()
                    val name = c.getString(1).orEmpty()
                    if (type.isBlank() || name.isBlank()) continue
                    if (!name.contains("@") && !isMailType(type)) continue
                    out.add(type to name)
                }
            }
        }
        // Device accounts even with no contacts yet — best effort, may need GET_ACCOUNTS.
        runCatching {
            val am = AccountManager.get(context)
            for (a in am.accounts) {
                if (a.name.contains("@") || isMailType(a.type)) out.add(a.type to a.name)
            }
        }
        // Contact groups expose Google accounts with READ_CONTACTS only.
        runCatching {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(
                    ContactsContract.Groups.ACCOUNT_TYPE,
                    ContactsContract.Groups.ACCOUNT_NAME
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0).orEmpty()
                    val name = c.getString(1).orEmpty()
                    if (type.isBlank() || name.isBlank()) continue
                    if (!name.contains("@") && !isMailType(type)) continue
                    out.add(type to name)
                }
            }
        }
        return out.distinct()
    }

    private fun isMailType(type: String): Boolean {
        val t = type.lowercase()
        return t.contains("google") || t.contains("exchange") || t.contains("mail") ||
            t.contains("outlook") || t.contains("hotmail") || t.contains("yahoo")
    }

    private data class SimSlot(val slot: Int, val subId: Int, val label: String, val detail: String)

    /** One entry per active SIM: SIM 1 (Grameenphone), SIM 2 (Robi), ... */
    private fun simSlots(context: Context): List<SimSlot> {
        val out = ArrayList<SimSlot>()
        // Full details when phone-state permission is granted.
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return@runCatching
            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return@runCatching
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
            ) return@runCatching
            val subs = sm.activeSubscriptionInfoList ?: return@runCatching
            for (s in subs) {
                val slot = s.simSlotIndex
                val subId = runCatching { s.subscriptionId }.getOrDefault(slot)
                val carrier = listOfNotNull(
                    s.carrierName?.toString()?.takeIf { it.isNotBlank() },
                    s.displayName?.toString()?.takeIf { it.isNotBlank() }
                ).firstOrNull() ?: "SIM ${slot + 1}"
                val num = runCatching { s.number?.takeIf { it.isNotBlank() } }.getOrNull()
                out.add(
                    SimSlot(
                        slot = slot,
                        subId = subId,
                        label = "SIM ${slot + 1} ($carrier)",
                        detail = if (num != null) "$carrier • $num" else carrier
                    )
                )
            }
        }
        if (out.isNotEmpty()) return out.sortedBy { it.slot }.distinctBy { it.slot }
        // No permission / OEM returned nothing: still show every SIM slot so SIM 2
        // is never hidden. TelephonyManager.phoneCount needs no permission.
        runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val count = runCatching { tm?.phoneCount ?: 1 }.getOrDefault(1)
            val n = count.coerceIn(1, 4)
            for (slot in 0 until n) {
                out.add(SimSlot(slot = slot, subId = slot, label = "SIM ${slot + 1}", detail = "SIM card"))
            }
        }
        return out.sortedBy { it.slot }.distinctBy { it.slot }
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
        destination: SaveDestination? = null
    ): Boolean {
        if (destination?.isSim == true) return saveToSim(context, name, phone, destination.simSlot, destination.simSubId)
        return try {
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

    /** SIM cards only store name + number, so photo is skipped there. */
    private fun saveToSim(context: Context, name: String, phone: String, slot: Int, subId: Int = -1): Boolean {
        val cleanName = name.trim().take(40)
        val cleanPhone = phone.filter { it.isDigit() || it == '+' }.trim()
        if (cleanName.isEmpty() || cleanPhone.filter { it.isDigit() }.length < 7) return false
        // Try the SIM-specific URI first (by subscription id, then slot), then generic ones.
        val bases = buildList {
            if (subId >= 0) {
                add("content://icc/adn/subId/$subId")
                add("content://icc/adn/subscription/$subId")
            }
            if (slot >= 0) {
                add("content://icc/adn/subId/$slot")
                add("content://icc/adn/sim$slot")
                add("content://icc/adn/slot$slot")
            }
            add("content://icc/adn")
            add("content://sim/adn")
        }
        for (base in bases) {
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
