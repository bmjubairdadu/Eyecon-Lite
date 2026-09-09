package com.eyeconlite.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CardImageMaker {

    const val W = 1080
    const val H = 1920

    fun buildCard(context: Context, info: CallerInfo, photo: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // --- background: deep navy gradient ---
        p.shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(0xFF0A1628.toInt(), 0xFF10294D.toInt(), 0xFF0B1C36.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // --- top glow blob (blue) ---
        p.color = 0xFF2196F3.toInt()
        p.alpha = 46
        c.drawCircle(W / 2f, 250f, 520f, p)
        p.alpha = 255

        // --- blue top accent bar ---
        p.color = 0xFF2196F3.toInt()
        c.drawRoundRect(RectF(60f, 60f, (W - 60).toFloat(), 76f), 8f, 8f, p)

        // --- app logo (call.png, circular, centered) ---
        val headerCx = W / 2f
        try {
            val logoSrc = BitmapFactory.decodeResource(context.resources, com.eyeconlite.R.drawable.app_logo)
            if (logoSrc != null) {
                val lr = 68f
                val lSize = (lr * 2).toInt()
                val scaled = Bitmap.createScaledBitmap(logoSrc, lSize, lSize, true)
                val lpath = Path()
                lpath.addCircle(headerCx, 160f, lr, Path.Direction.CW)
                c.save()
                c.clipPath(lpath)
                c.drawBitmap(scaled, headerCx - lr, 160f - lr, p)
                c.restore()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.color = 0xFFFFFFFF.toInt()
                c.drawCircle(headerCx, 160f, lr + 2f, p)
                p.style = Paint.Style.FILL
            }
        } catch (_: Exception) { }

        // --- branding ---
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 54f
        c.drawText("EYECON  LITE", W / 2f, 268f, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 32f
        p.color = 0xFF9FC6EE.toInt()
        c.drawText("Finding  Anyone", W / 2f, 310f, p)

        // --- glass card ---
        val cardTop = 350f
        val cardBottom = 1660f
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 22
        c.drawRoundRect(RectF(70f, cardTop, (W - 70).toFloat(), cardBottom), 48f, 48f, p)
        p.alpha = 255
        // card border
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0x55FFFFFF.toInt()
        c.drawRoundRect(RectF(70f, cardTop, (W - 70).toFloat(), cardBottom), 48f, 48f, p)
        p.style = Paint.Style.FILL

        // --- photo circle ---
        val cx = W / 2f
        val cy = cardTop + 240f
        val r = 180f
        // ring
        p.color = 0xFF2196F3.toInt()
        c.drawCircle(cx, cy, r + 14f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, r + 6f, p)

        if (photo != null) {
            val path = Path()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            c.save()
            c.clipPath(path)
            val scaled = Bitmap.createScaledBitmap(photo, (r * 2).toInt(), (r * 2).toInt(), true)
            c.drawBitmap(scaled, cx - r, cy - r, p)
            c.restore()
        } else {
            p.color = 0xFF1B3A5F.toInt()
            c.drawCircle(cx, cy, r, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textSize = 160f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val initial = info.name.trim().firstOrNull()?.uppercase() ?: "?"
            c.drawText(initial, cx, cy + 55f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // --- name ---
        var y = cy + r + 80f
        p.color = 0xFFFFFFFF.toInt()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 60f
        c.drawText(fitText(info.name.ifBlank { "Unknown" }, p, 880f), cx, y, p)

        // --- phone pill ---
        y += 78f
        val phone = info.phone
        p.textSize = 42f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val pillW = p.measureText(phone) + 110f
        p.color = 0xFF2196F3.toInt()
        c.drawRoundRect(RectF(cx - pillW / 2, y - 58f, cx + pillW / 2, y + 20f), 40f, 40f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(phone, cx, y, p)

        // --- divider ---
        y += 52f
        p.color = 0x33FFFFFF.toInt()
        c.drawRect(150f, y, (W - 150).toFloat(), y + 2f, p)

        // --- detail rows ---
        y += 28f
        p.textAlign = Paint.Align.LEFT
        val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val rows = listOf(
            "Name" to info.name.ifBlank { "Unknown" },
            "Number" to phone,
            "Tag" to info.tag.ifBlank { "—" },
            "Source" to "Eyecon",
            "Checked" to date
        )
        for ((label, value) in rows) {
            y += 70f
            p.color = 0xFF8FB4D8.toInt()
            p.textSize = 28f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(label.uppercase(), 150f, y, p)
            y += 46f
            p.color = 0xFFFFFFFF.toInt()
            p.textSize = 40f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            c.drawText(fitText(value, p, 780f), 150f, y, p)
            y += 10f
        }

        // --- footer logo + text ---
        try {
            val fSrc = BitmapFactory.decodeResource(context.resources, com.eyeconlite.R.drawable.app_logo)
            if (fSrc != null) {
                val fr = 30f
                val fSize = (fr * 2).toInt()
                val fScaled = Bitmap.createScaledBitmap(fSrc, fSize, fSize, true)
                val fpath = Path()
                fpath.addCircle(cx, H - 165f, fr, Path.Direction.CW)
                c.save()
                c.clipPath(fpath)
                c.drawBitmap(fScaled, cx - fr, H - 165f - fr, p)
                c.restore()
            }
        } catch (_: Exception) { }
        p.textAlign = Paint.Align.CENTER
        p.color = 0xFF7FA8CC.toInt()
        p.textSize = 28f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        c.drawText("Generated by Eyecon Lite  •  Finding Anyone", cx, H - 105f, p)
        p.color = 0xFF2196F3.toInt()
        p.textSize = 28f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("eyecon lite", cx, H - 65f, p)

        return bmp
    }

    private fun fitText(text: String, paint: Paint, maxW: Float): String {
        var t = text
        if (paint.measureText(t) <= maxW) return t
        while (t.length > 4 && paint.measureText("$t…") > maxW) {
            // binary-ish trim
            t = t.dropLast(2)
        }
        return "$t…"
    }

    /** Saves portrait PNG directly into Download folder. Returns display path/uri string. */
    fun saveToDownloads(context: Context, bitmap: Bitmap, phone: String): String {
        val clean = phone.filter { it.isDigit() }.ifBlank { "unknown" }
        val fileName = "EyeconLite_$clean.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create file in Downloads")
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Failed to write PNG")
                }
            } ?: throw IllegalStateException("Cannot open Downloads file")
            "Downloads/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName)
            FileOutputStream(f).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            f.absolutePath
        }
    }
}
