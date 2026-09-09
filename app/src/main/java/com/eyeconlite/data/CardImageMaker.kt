package com.eyeconlite.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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

        // --- premium background: deep navy + ambient gold/blue glows ---
        p.shader = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            intArrayOf(0xFF060D1A.toInt(), 0xFF0B1E3A.toInt(), 0xFF05090F.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        drawGlow(c, W * 0.88f, 180f, 520f, 0xFFD4AF37.toInt(), 52)
        drawGlow(c, W * 0.08f, H * 0.72f, 560f, 0xFF2196F3.toInt(), 46)
        drawGlow(c, W * 0.5f, H * 0.45f, 720f, 0xFF14335C.toInt(), 60)

        // subtle dot texture
        p.color = 0xFFFFFFFF.toInt()
        var dotY = 340f
        while (dotY < H - 220) {
            var dotX = 70f
            while (dotX < W - 60) {
                p.alpha = 12
                c.drawCircle(dotX, dotY, 2.2f, p)
                dotX += 58f
            }
            dotY += 58f
        }
        p.alpha = 255

        // vignette edges
        p.shader = RadialGradient(
            W / 2f, H / 2f, 1150f,
            intArrayOf(0x00000000, 0x99000000.toInt()),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // premium frame: gold hairline + gold top beam
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0xFFD4AF37.toInt()
        p.alpha = 130
        c.drawRoundRect(RectF(22f, 22f, W - 22f, H - 22f), 40f, 40f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, W.toFloat(), 0f,
            intArrayOf(0xFF9A7B1E.toInt(), 0xFFF5D67B.toInt(), 0xFFD4AF37.toInt(), 0xFFF5D67B.toInt(), 0xFF9A7B1E.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(48f, 46f, W - 48f, 58f), 6f, 6f, p)
        p.shader = null

        // --- premium header: gold-ring logo + two-tone brand + verified seal ---
        val logoCx = 150f
        val logoCy = 200f
        val logoR = 60f
        drawGlow(c, logoCx, logoCy, 112f, 0xFFD4AF37.toInt(), 40)
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
                p.strokeWidth = 5f
                p.color = 0xFFD4AF37.toInt()
                c.drawCircle(logoCx, logoCy, logoR + 3f, p)
                p.strokeWidth = 2f
                p.color = 0xFFFFFFFF.toInt()
                p.alpha = 200
                c.drawCircle(logoCx, logoCy, logoR + 10f, p)
                p.alpha = 255
                p.style = Paint.Style.FILL
            }
        } catch (_: Exception) { }

        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 58f
        p.color = 0xFFFFFFFF.toInt()
        val brand1 = "EYECON "
        c.drawText(brand1, 232f, 196f, p)
        p.color = 0xFFF5D67B.toInt()
        c.drawText("LITE", 232f + p.measureText(brand1), 196f, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 27f
        p.color = 0xFF9FC6EE.toInt()
        c.drawText("F I N D I N G   A N Y O N E", 235f, 238f, p)

        // verified seal (dark pill, gold border + gold text)
        val seal = "\u2605 VERIFIED"
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 25f
        val sealW = p.measureText(seal) + 64f
        val sealR = RectF(W - 60f - sealW, 158f, W - 60f, 220f)
        p.color = 0xCC0A1628.toInt()
        c.drawRoundRect(sealR, 31f, 31f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        p.color = 0xFFD4AF37.toInt()
        c.drawRoundRect(sealR, 31f, 31f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFF5D67B.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(seal, sealR.centerX(), 198f, p)
        p.textAlign = Paint.Align.LEFT

        // --- premium hero: drop shadow + double gold frame + cover photo ---
        val cx = W / 2f
        val hero = RectF(52f, 318f, (W - 52).toFloat(), 1148f)
        val heroR = 44f
        val phone = info.phone
        p.color = 0x99000000.toInt()
        c.drawRoundRect(RectF(hero.left, hero.top + 24f, hero.right, hero.bottom + 24f), heroR, heroR, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xFFD4AF37.toInt()
        c.drawRoundRect(RectF(hero.left - 7f, hero.top - 7f, hero.right + 7f, hero.bottom + 7f), heroR + 7f, heroR + 7f, p)
        p.strokeWidth = 2f
        p.color = 0xFFF5D67B.toInt()
        p.alpha = 160
        c.drawRoundRect(RectF(hero.left - 13f, hero.top - 13f, hero.right + 13f, hero.bottom + 13f), heroR + 13f, heroR + 13f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        if (photo != null) {
            drawCover(c, photo, hero, heroR, p)
        } else {
            p.shader = LinearGradient(
                0f, hero.top, 0f, hero.bottom,
                intArrayOf(0xFF274E7D.toInt(), 0xFF10294D.toInt(), 0xFF0A1628.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            val ph = Path()
            ph.addRoundRect(hero, heroR, heroR, Path.Direction.CW)
            c.save()
            c.clipPath(ph)
            c.drawRect(hero, p)
            c.restore()
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = 0xFFD4AF37.toInt()
            p.alpha = 170
            c.drawCircle(cx, hero.centerY() - 40f, 150f, p)
            p.alpha = 255
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 220f
            val initial = info.name.trim().firstOrNull()?.uppercase() ?: "?"
            c.drawText(initial, cx, hero.centerY() + 35f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        // inner top highlight + gold corner ornaments
        val hlPath = Path()
        hlPath.addRoundRect(hero, heroR, heroR, Path.Direction.CW)
        c.save()
        c.clipPath(hlPath)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 50
        c.drawRect(hero.left, hero.top, hero.right, hero.top + 5f, p)
        p.alpha = 255
        c.restore()
        drawCorners(c, hero)

        // premium seal badge (gold when photo, dark when not)
        val badge = if (photo != null) "\u2605 PREMIUM" else "NO PHOTO"
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 25f
        val bw = p.measureText(badge) + 64f
        val bRect = RectF(hero.right - bw - 28f, hero.top + 24f, hero.right - 28f, hero.top + 86f)
        if (photo != null) {
            p.shader = LinearGradient(
                0f, bRect.top, 0f, bRect.bottom,
                intArrayOf(0xFFF5D67B.toInt(), 0xFFD4AF37.toInt()),
                null, Shader.TileMode.CLAMP
            )
            c.drawRoundRect(bRect, 31f, 31f, p)
            p.shader = null
            p.color = 0xFF3A2E05.toInt()
        } else {
            p.color = 0xDD0A1628.toInt()
            c.drawRoundRect(bRect, 31f, 31f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = 0xFFD4AF37.toInt()
            c.drawRoundRect(bRect, 31f, 31f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFF5D67B.toInt()
        }
        p.textAlign = Paint.Align.CENTER
        c.drawText(badge, bRect.centerX(), bRect.top + 41f, p)

        // cinematic bottom scrim for readable overlay text
        val scrimTop = hero.bottom - 360f
        val scPath = Path()
        scPath.addRoundRect(hero, heroR, heroR, Path.Direction.CW)
        c.save()
        c.clipPath(scPath)
        p.shader = LinearGradient(
            0f, scrimTop, 0f, hero.bottom,
            intArrayOf(0x00000000, 0x99000000.toInt(), 0xE6000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(hero.left, scrimTop, hero.right, hero.bottom, p)
        c.restore()
        p.shader = null

        // name (shadowed) + gold rule + gold phone pill overlaid on photo
        val heroPhone = info.normalized?.nationalFormat ?: phone
        val heroName = fitText(info.name.ifBlank { "Unknown" }, p.apply { textSize = 66f }, hero.width() - 120f)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 66f
        p.color = 0x99000000.toInt()
        c.drawText(heroName, hero.left + 60f, hero.bottom - 126f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(heroName, hero.left + 56f, hero.bottom - 130f, p)
        p.shader = LinearGradient(
            hero.left + 56f, 0f, hero.left + 196f, 0f,
            intArrayOf(0xFFF5D67B.toInt(), 0xFFD4AF37.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(hero.left + 56f, hero.bottom - 112f, hero.left + 196f, hero.bottom - 104f), 4f, 4f, p)
        p.shader = null
        p.textSize = 40f
        val pillW2 = p.measureText(heroPhone) + 110f
        val pillRect = RectF(hero.left + 56f, hero.bottom - 92f, hero.left + 56f + pillW2, hero.bottom - 28f)
        p.shader = LinearGradient(
            0f, pillRect.top, 0f, pillRect.bottom,
            intArrayOf(0xFFF5D67B.toInt(), 0xFFD4AF37.toInt(), 0xFFB8912A.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(pillRect, 32f, 32f, p)
        p.shader = null
        p.color = 0xFF3A2E05.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(heroPhone, pillRect.centerX(), hero.bottom - 48f, p)

        // --- premium info card: dark luxe panel, gold top edge ---
        val card = RectF(52f, 1188f, (W - 52).toFloat(), 1700f)
        p.color = 0x99000000.toInt()
        c.drawRoundRect(RectF(card.left, card.top + 16f, card.right, card.bottom + 16f), 38f, 38f, p)
        p.color = 0xF20C1B30.toInt()
        c.drawRoundRect(card, 38f, 38f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        p.color = 0xFFD4AF37.toInt()
        p.alpha = 110
        c.drawRoundRect(card, 38f, 38f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            card.left + 60f, 0f, card.right - 60f, 0f,
            intArrayOf(0x009A7B1E, 0xFFD4AF37.toInt(), 0x009A7B1E),
            null, Shader.TileMode.CLAMP
        )
        val goldTop = Path()
        goldTop.addRoundRect(card, 38f, 38f, Path.Direction.CW)
        c.save()
        c.clipPath(goldTop)
        c.drawRect(card.left, card.top, card.right, card.top + 4f, p)
        c.restore()
        p.shader = null

        val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(info.checkedAt))
        val nn = info.normalized
        val nationalPhone = nn?.nationalFormat ?: phone
        val opLine = listOfNotNull(
            nn?.operator?.takeIf { it != "Unknown" && it.isNotBlank() },
            nn?.numberType?.takeIf { it != "Unknown" && it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Unknown" }
        val rows = listOf(
            "Country" to (if (nn != null) "${nn.countryName} (+${nn.countryCode})" else "Unknown"),
            "Operator" to opLine,
            "Number" to nationalPhone,
            "Tag" to info.tag.ifBlank { "No tag" },
            "Checked" to date
        )
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4AF37.toInt()
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        var y = 1270f
        for ((label, value) in rows) {
            val lab = label.uppercase() + "   "
            c.drawText(lab, 112f, y, labelPaint)
            val lw = labelPaint.measureText(lab)
            val maxW = card.right - 70f - (112f + lw)
            c.drawText(fitText(value, valuePaint, maxW), 112f + lw, y + 4f, valuePaint)
            if (label != "Checked") {
                y += 84f
                p.color = 0xFFD4AF37.toInt()
                p.alpha = 36
                c.drawRect(104f, y - 32f, card.right - 64f, y - 30f, p)
                p.alpha = 255
            }
        }

        // --- premium footer: gold divider + diamond, ringed logo, credits ---
        p.color = 0xFFD4AF37.toInt()
        p.alpha = 140
        c.drawRect(120f, 1750f, W - 120f, 1752.5f, p)
        p.alpha = 255
        p.color = 0xFFF5D67B.toInt()
        c.save()
        c.rotate(45f, cx, 1751f)
        c.drawRect(cx - 9f, 1751f - 9f, cx + 9f, 1751f + 9f, p)
        c.restore()
        try {
            val fSrc = BitmapFactory.decodeResource(context.resources, com.eyeconlite.R.drawable.app_logo)
            if (fSrc != null) {
                val fr = 24f
                val fSize = (fr * 2).toInt()
                val fScaled = Bitmap.createScaledBitmap(fSrc, fSize, fSize, true)
                val fpath = Path()
                fpath.addCircle(cx, 1798f, fr, Path.Direction.CW)
                c.save()
                c.clipPath(fpath)
                c.drawBitmap(fScaled, cx - fr, 1798f - fr, p)
                c.restore()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = 0xFFD4AF37.toInt()
                c.drawCircle(cx, 1798f, fr + 2f, p)
                p.style = Paint.Style.FILL
            }
        } catch (_: Exception) { }
        p.textAlign = Paint.Align.CENTER
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 27f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("Developed by JUBAIR HOSEN", cx, 1852f, p)
        p.color = 0xFFD4AF37.toInt()
        p.textSize = 23f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("EYECON LITE  \u2022  FINDING ANYONE", cx, 1882f, p)

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

    private fun drawGlow(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(setAlpha(color, alpha), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius, glow)
    }

    private fun setAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
    }

    private fun drawCorners(c: Canvas, r: RectF) {
        val len = 72f
        val inset = 26f
        val cp = Paint(Paint.ANTI_ALIAS_FLAG)
        cp.style = Paint.Style.STROKE
        cp.strokeWidth = 8f
        cp.color = 0xFFF5D67B.toInt()
        cp.strokeCap = Paint.Cap.ROUND
        c.drawLine(r.left + inset, r.top + inset + len, r.left + inset, r.top + inset, cp)
        c.drawLine(r.left + inset, r.top + inset, r.left + inset + len, r.top + inset, cp)
        c.drawLine(r.right - inset - len, r.top + inset, r.right - inset, r.top + inset, cp)
        c.drawLine(r.right - inset, r.top + inset, r.right - inset, r.top + inset + len, cp)
        c.drawLine(r.left + inset, r.bottom - inset - len, r.left + inset, r.bottom - inset, cp)
        c.drawLine(r.left + inset, r.bottom - inset, r.left + inset + len, r.bottom - inset, cp)
        c.drawLine(r.right - inset - len, r.bottom - inset, r.right - inset, r.bottom - inset, cp)
        c.drawLine(r.right - inset, r.bottom - inset - len, r.right - inset, r.bottom - inset, cp)
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
