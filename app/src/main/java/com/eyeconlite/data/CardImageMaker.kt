package com.eyeconlite.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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

        // --- header: app logo + brand (left), data pill (right) ---
        val headerCx = W / 2f
        val logoCx = 150f
        val logoCy = 205f
        val logoR = 62f
        try {
            val logoSrc = BitmapFactory.decodeResource(context.resources, com.eyeconlite.R.drawable.app_logo)
            if (logoSrc != null) {
                val lSize = (logoR * 2).toInt()
                val scaled = Bitmap.createScaledBitmap(logoSrc, lSize, lSize, true)
                val lpath = Path()
                lpath.addCircle(logoCx, logoCy, logoR, Path.Direction.CW)
                c.save()
                c.clipPath(lpath)
                c.drawBitmap(scaled, logoCx - logoR, logoCy - logoR, p)
                c.restore()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.color = 0xFFFFFFFF.toInt()
                c.drawCircle(logoCx, logoCy, logoR + 2f, p)
                p.style = Paint.Style.FILL
            }
        } catch (_: Exception) { }

        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 56f
        c.drawText("EYECON LITE", 235f, 200f, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 30f
        p.color = 0xFF9FC6EE.toInt()
        c.drawText("Finding Anyone", 238f, 242f, p)

        // data-source pill (top-right)
        val pillLabel = "EYECON DATA"
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 26f
        val pillW = p.measureText(pillLabel) + 70f
        val pillR = RectF(W - 60f - pillW, 160f, W - 60f, 224f)
        p.color = 0xFF0B7B3E.toInt()
        c.drawRoundRect(pillR, 32f, 32f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(pillLabel, pillR.centerX(), 203f, p)
        p.textAlign = Paint.Align.LEFT

        // --- hero photo card (full cover, rounded) ---
        val cx = W / 2f
        val hero = RectF(48f, 330f, (W - 48).toFloat(), 1160f)
        val heroR = 46f
        val phone = info.phone
        if (photo != null) {
            drawCover(c, photo, hero, heroR, p)
        } else {
            p.shader = LinearGradient(
                0f, hero.top, 0f, hero.bottom,
                intArrayOf(0xFF1B5FA8.toInt(), 0xFF0D3B6E.toInt()),
                null, Shader.TileMode.CLAMP
            )
            val ph = Path()
            ph.addRoundRect(hero, heroR, heroR, Path.Direction.CW)
            c.save()
            c.clipPath(ph)
            c.drawRect(hero, p)
            c.restore()
            p.shader = null
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 300f
            p.alpha = 235
            val initial = info.name.trim().firstOrNull()?.uppercase() ?: "?"
            c.drawText(initial, cx, hero.centerY() + 100f, p)
            p.alpha = 255
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        // hero border
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0x55FFFFFF.toInt()
        c.drawRoundRect(hero, heroR, heroR, p)
        p.style = Paint.Style.FILL

        // photo status badge (inside hero, top-right)
        val badge = if (photo != null) "HD PHOTO" else "NO PHOTO"
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 26f
        val bw = p.measureText(badge) + 56f
        val bRect = RectF(hero.right - bw - 28f, hero.top + 24f, hero.right - 28f, hero.top + 84f)
        p.color = if (photo != null) 0xCC0B7B3E.toInt() else 0xAA5B6B82.toInt()
        c.drawRoundRect(bRect, 30f, 30f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(badge, bRect.centerX(), bRect.top + 40f, p)

        // bottom scrim for readable overlay text
        val scrimTop = hero.bottom - 330f
        val scPath = Path()
        scPath.addRoundRect(hero, heroR, heroR, Path.Direction.CW)
        c.save()
        c.clipPath(scPath)
        p.shader = LinearGradient(
            0f, scrimTop, 0f, hero.bottom,
            intArrayOf(0x00000000, 0xD8000000.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(hero.left, scrimTop, hero.right, hero.bottom, p)
        c.restore()
        p.shader = null

        // name + phone pill overlaid on photo
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 64f
        c.drawText(fitText(info.name.ifBlank { "Unknown" }, p, hero.width() - 120f), hero.left + 56f, hero.bottom - 130f, p)
        p.textSize = 40f
        val pillW2 = p.measureText(phone) + 96f
        val pillRect = RectF(hero.left + 56f, hero.bottom - 104f, hero.left + 56f + pillW2, hero.bottom - 36f)
        p.color = 0xFF2196F3.toInt()
        c.drawRoundRect(pillRect, 34f, 34f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(phone, pillRect.centerX(), hero.bottom - 56f, p)

        // --- info glass card ---
        val card = RectF(48f, 1200f, (W - 48).toFloat(), 1720f)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 20
        c.drawRoundRect(card, 40f, 40f, p)
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0x55FFFFFF.toInt()
        c.drawRoundRect(card, 40f, 40f, p)
        p.style = Paint.Style.FILL

        val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(info.checkedAt))
        val nn = info.normalized
        val opLine = listOfNotNull(
            nn?.operator?.takeIf { it != "—" },
            nn?.numberType?.takeIf { it != "—" && it != "Unknown" }
        ).joinToString(" • ").ifBlank { "—" }
        val rows = listOf(
            "Country" to (if (nn != null) "${nn.countryName} (+${nn.countryCode})" else "—"),
            "Operator" to opLine,
            "National" to (nn?.nationalFormat ?: phone),
            "Intl" to (nn?.prettyInternational ?: phone),
            "Tag" to info.tag.ifBlank { "—" },
            "Checked" to date
        )
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8FB4D8.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        var y = 1276f
        for ((label, value) in rows) {
            val lab = label.uppercase() + "   "
            c.drawText(lab, 110f, y, labelPaint)
            val lw = labelPaint.measureText(lab)
            val maxW = card.right - 70f - (110f + lw)
            c.drawText(fitText(value, valuePaint, maxW), 110f + lw, y + 4f, valuePaint)
            if (label != "Checked") {
                y += 78f
                p.color = 0x22FFFFFF.toInt()
                c.drawRect(100f, y - 30f, card.right - 60f, y - 28f, p)
            }
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
        c.drawText("Generated by Eyecon Lite  •  Finding Anyone", cx, H - 145f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 30f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("Developed by JUBAIR HOSEN", cx, H - 100f, p)
        p.color = 0xFF2196F3.toInt()
        p.textSize = 28f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("eyecon lite", cx, H - 60f, p)

        return bmp
    }

    private fun drawCover(c: Canvas, photo: Bitmap, dst: RectF, radius: Float, p: Paint) {
        val path = Path()
        path.addRoundRect(dst, radius, radius, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        val scale = maxOf(dst.width() / photo.width, dst.height() / photo.height)
        val sw = dst.width() / scale
        val sh = dst.height() / scale
        val sx = (photo.width - sw) / 2f
        val sy = (photo.height - sh) / 2f
        val src = Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        c.drawBitmap(photo, src, dst, p)
        c.restore()
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
