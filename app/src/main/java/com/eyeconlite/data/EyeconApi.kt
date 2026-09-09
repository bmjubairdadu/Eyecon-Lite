package com.eyeconlite.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class CallerInfo(
    val name: String,
    val phone: String,
    val tag: String = "",
    val photoBytes: ByteArray? = null,
    val normalized: NormalizedNumber? = null,
    val checkedAt: Long = System.currentTimeMillis()
) {
    val hasPhoto: Boolean get() = photoBytes != null && photoBytes!!.isNotEmpty()
    val photoStatus: String get() =
        if (hasPhoto) "Available (${(photoBytes!!.size / 1024)} KB)" else "Not found"
}

object EyeconApi {
    private const val BASE = "https://api.eyecon-app.com"
    private const val CV = "vc_786_vn_4.2026.09.06.1153_a"

    // Captured from real Eyecon HAR (session headers). Static per install in HAR.
    private const val E_AUTH = "9be0d99b-4f5a-477d-97b2-b8e809781e15"
    private const val E_AUTH_C = "37"
    private const val E_AUTH_K = "PgdtSBeR0MumR7fO"
    private const val E_AUTH_V = "e1"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun baseBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .header("Accept-Charset", "UTF-8")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .header("e-auth-v", E_AUTH_V)
            .header("e-auth", E_AUTH)
            .header("e-auth-c", E_AUTH_C)
            .header("e-auth-k", E_AUTH_K)
            .header("Accept-Encoding", "gzip")
    }

    suspend fun lookup(rawInput: String): CallerInfo = withContext(Dispatchers.IO) {
        // Normalize first: +880 17XX-XXXXXX / 0088017... / 017XXXXXXXX / (017)... all -> e164
        val norm = PhoneUtils.normalize(rawInput)
        val variants = PhoneUtils.lookupVariants(norm)

        var name = ""
        var type = ""
        var authBlocked = false
        var networkError: String? = null
        // Try e164 first, then local variants (trunk-zero etc.)
        for (cli in variants) {
            val nameUrl = "$BASE/app/getnames.jsp?cli=$cli&lang=en&is_callerid=true&is_ic=true" +
                "&cv=$CV&requestApi=URLconnection&source=EyeconLite"
            try {
                baseBuilder(nameUrl).get().build().let { req ->
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && body.isNotBlank()) {
                            try {
                                val arr = JSONArray(body)
                                if (arr.length() > 0) {
                                    val o = arr.getJSONObject(0)
                                    name = o.optString("name", "")
                                    type = o.optString("type", "")
                                }
                            } catch (_: Exception) { }
                        } else if (resp.code == 403 || resp.code == 401) {
                            authBlocked = true
                        }
                    }
                }
            } catch (e: Exception) {
                networkError = e.message
            }
            if (name.isNotBlank()) break
        }
        if (authBlocked && name.isBlank()) {
            throw IllegalStateException("Eyecon session expired (auth blocked). Try again later.")
        }
        if (name.isBlank() && networkError != null && variants.size == 1) {
            // keep silent: photo may still resolve; only hard-fail when nothing tried well
        }

        val photo = fetchPhotoForNumber(rawInput)
        CallerInfo(
            name = name.ifBlank { "Unknown" },
            phone = norm.displayPlus,
            tag = type,
            photoBytes = photo,
            normalized = norm
        )
    }

    /** Photo-only lookup: try normalized variants, return first bytes found (or null). */
    suspend fun fetchPhotoForNumber(rawInput: String): ByteArray? = withContext(Dispatchers.IO) {
        val norm = try {
            PhoneUtils.normalize(rawInput)
        } catch (_: Exception) {
            return@withContext null
        }
        for (cli in PhoneUtils.lookupVariants(norm)) {
            try {
                val bytes = fetchPhoto(cli)
                if (bytes != null) return@withContext bytes
            } catch (_: Exception) {
            }
        }
        null
    }

    private fun fetchPhoto(cli: String): ByteArray? {
        // type=0 (caller-id pic) first, then type=1 (contact pic) fallback — both seen in HAR
        val urls = listOf(
            "$BASE/app/pic?cli=$cli&is_callerid=true&size=big&type=0&src=EyeconLite&cancelfresh=0&cv=$CV",
            "$BASE/app/pic?cli=$cli&size=big&type=1"
        )
        for (u in urls) {
            try {
                val req = baseBuilder(u).header("Accept", "image/*").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.size > 2000) return bytes
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    fun decodePhoto(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }
}
