package com.eyeconlite.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tag: String,          // e.g. v1.7
    val versionName: String,  // e.g. 1.7
    val notes: String,
    val apkUrl: String,
    val apkSize: Long
)

class UpdateCheckException(message: String) : IllegalStateException(message)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val message: String = "You have the latest version") : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val file: File, val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

object AppUpdater {
    const val REPO = "bmjubairdadu/Eyecon-Lite"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val WEB_LATEST = "https://github.com/$REPO/releases/latest"
    private const val WEB_ATOM = "https://github.com/$REPO/releases.atom"
    private const val APK_NAME = "Eyecon.Lite.apk"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val webClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient: OkHttpClient by lazy {
        webClient.newBuilder().followRedirects(false).build()
    }

    suspend fun checkLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val apiError = try {
            return@withContext checkViaApi()
        } catch (e: Exception) {
            e
        }
        // API failed (rate limit or offline) — use the public website instead. No token needed.
        try {
            checkViaWeb()
        } catch (_: Exception) {
            if (apiError is UpdateCheckException) throw apiError
            throw UpdateCheckException("Couldn't check right now. Try again later.")
        }
    }

    private fun checkViaApi(): UpdateInfo {
        val req = Request.Builder()
            .url(API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EyeconLite-Updater")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 403 || resp.code == 429) {
                throw UpdateCheckException("Couldn't check right now. Try again later.")
            }
            if (!resp.isSuccessful) throw UpdateCheckException("Couldn't check right now. Try again later.")
            val json = JSONObject(resp.body?.string().orEmpty())
            val tag = json.optString("tag_name", "").trim()
            if (tag.isEmpty()) throw IllegalStateException("No release found")
            val notes = json.optString("body", "")
            var apkUrl = ""
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
            }
            if (apkUrl.isEmpty()) throw IllegalStateException("No APK in latest release")
            return UpdateInfo(tag, tag.trimStart('v', 'V'), notes, apkUrl, apkSize)
        }
    }

    /** Website fallback when the API is rate-limited or offline: no token needed. */
    private fun checkViaWeb(): UpdateInfo {
        val redirectTag = latestTagFromRedirect()
        val feed = if (redirectTag != null) null else atomFeed()
        val tag = redirectTag ?: feed?.let(::tagFromFeed)
            ?: throw UpdateCheckException("Couldn't check right now. Try again later.")
        val notes = feed?.let(::notesFromFeed).orEmpty()
        val apkUrl = "https://github.com/$REPO/releases/download/$tag/$APK_NAME"
        return UpdateInfo(tag, tag.trimStart('v', 'V'), notes, apkUrl, 0L)
    }

    private fun latestTagFromRedirect(): String? {
        return try {
            val req = Request.Builder()
                .url(WEB_LATEST)
                .header("User-Agent", "EyeconLite-Updater")
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location").orEmpty()
                Regex("/releases/tag/([^/?#]+)").find(loc)
                    ?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun atomFeed(): String? {
        return try {
            val req = Request.Builder()
                .url(WEB_ATOM)
                .header("User-Agent", "EyeconLite-Updater")
                .build()
            webClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tagFromFeed(feed: String): String? =
        Regex("/releases/tag/([^\"<>\\s]+)").find(feed)
            ?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun notesFromFeed(feed: String): String {
        val content = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
            .find(feed)?.groupValues?.getOrNull(1) ?: return ""
        return content
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim().take(600)
    }

    suspend fun fetchInstallEstimate(): Long? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EyeconLite-Stats")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 403 || resp.code == 429) return@withContext null
            if (!resp.isSuccessful) throw IllegalStateException("Stats unavailable (${resp.code})")
            val releases = org.json.JSONArray(resp.body?.string().orEmpty())
            var total = 0L
            for (i in 0 until releases.length()) {
                val assets = releases.getJSONObject(i).optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    total += assets.getJSONObject(j).optLong("download_count", 0L)
                }
            }
            total
        }
    }

    fun currentVersionName(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
    }

    /** true when remote tag (v1.7) is newer than current version (1.6). */
    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val r = parseVer(remoteTag)
        val c = parseVer(currentVersion)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVer(s: String): List<Int> {
        return s.trim().trimStart('v', 'V')
            .split(".", "-", "_")
            .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
    }

    /** Download APK with progress callback (0f..1f). */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, "EyeconLite-${info.versionName}.apk")
        val req = Request.Builder()
            .url(info.apkUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "EyeconLite-Updater")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Download failed (${resp.code})")
            val body = resp.body ?: throw IllegalStateException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    fun canInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownSourcesSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

