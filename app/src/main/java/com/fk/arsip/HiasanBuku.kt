package com.fk.arsip

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hiasan fisik buku untuk tekstur halaman (efek ala PageCurl_BookView, bertema kayu/emas):
 *  - jilid SPIRAL (cincin emas + lubang + bayangan lipatan) di sisi jilid,
 *  - tepi tumpukan halaman & bingkai sampul kayu di sisi luar,
 *  - label kecil "Pustaka FK" di pojok kanan bawah halaman,
 *  - kertas BALIK bergaris dengan watermark cincin tasbih (sisi belakang halaman).
 *
 * Semua digambar langsung pada bitmap tekstur layar (BUKAN pada bitmap konten tinggi), jadi
 * jilid selalu diam di tepi layar walau isi halaman di-scroll — seperti buku sungguhan.
 */
object HiasanBuku {
    const val HALAMAN = 0
    const val SAMPUL_DEPAN = 1
    const val SAMPUL_BELAKANG = 2

    private val kayu = Color.parseColor("#3A2313")

    // Cache hiasan (overlay transparan & sisi balik jadi) supaya SCROLL/refresh tekstur tidak
    // menggambar ulang gradasi + path jilid tiap kejadian sentuh -- itu yang bikin scroll kurang mulus.
    private val cacheOverlay = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 8
    }
    private val cacheBalik = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 6
    }

    fun bersihkanCache() {
        synchronized(cacheOverlay) { cacheOverlay.clear() }
        synchronized(cacheBalik) { cacheBalik.clear() }
    }

    private fun overlay(w: Int, h: Int, jenis: Int, d: Float, gelap: Boolean): Bitmap {
        val kunci = "$jenis:$w:$h:$gelap"
        synchronized(cacheOverlay) { cacheOverlay[kunci]?.let { if (!it.isRecycled) return it } }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        hiasiDepan(c, w, h, jenis, d, gelap)
        synchronized(cacheOverlay) { cacheOverlay[kunci] = bmp }
        return bmp
    }

    /** Potong bagian [offsetY] dari bitmap konten tinggi ke bitmap baru seukuran layar, lalu hiasi. */
    fun potongDanHiasi(
        sumber: Bitmap, w: Int, h: Int, offsetY: Int, jenis: Int, d: Float,
        gelap: Boolean = false, tertandai: Boolean = false
    ): Bitmap {
        val hasil = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(hasil)
        c.drawColor(if (gelap) Color.parseColor("#201B16") else Color.parseColor("#FFFDF7"))
        val maxOffset = (sumber.height - h).coerceAtLeast(0)
        val y0 = offsetY.coerceIn(0, maxOffset)
        val tinggi = min(h, sumber.height - y0).coerceAtLeast(1)
        val lebar = min(w, sumber.width).coerceAtLeast(1)
        c.drawBitmap(sumber, Rect(0, y0, lebar, y0 + tinggi), Rect(0, 0, lebar, tinggi), null)
        c.drawBitmap(overlay(w, h, jenis, d, gelap), 0f, 0f, null)
        if (tertandai && jenis == HALAMAN) gambarPin(c, w - 34f * d, 7f * d, d)
        return hasil
    }

    /**
     * Sisi BALIK: konten (terbaca normal) dibalik horizontal supaya tampil benar saat dipetakan
     * mirror oleh CurlView. Hasil jadi di-cache per [kunci]; yang dikembalikan SALINAN
     * (CurlPage.setTexture me-recycle bitmap yang diberikan).
     */
    fun balikanDariKonten(
        konten: Bitmap, w: Int, h: Int, d: Float,
        sampulLuar: Boolean = false, gelap: Boolean = false, kunci: String = ""
    ): Bitmap {
        val k = "$kunci:$w:$h:$sampulLuar:$gelap"
        if (kunci.isNotEmpty()) {
            val ada = synchronized(cacheBalik) { cacheBalik[k] }
            if (ada != null && !ada.isRecycled) return ada.copy(Bitmap.Config.ARGB_8888, false) ?: ada
        }
        val hasil = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(hasil)
        c.save()
        c.scale(-1f, 1f, w / 2f, h / 2f)
        c.drawBitmap(konten, Rect(0, 0, konten.width, konten.height), Rect(0, 0, w, h), null)
        c.restore()
        // Setelah dipetakan mirror, sisi KIRI tekstur tampil di tepi jilid (kanan halaman kiri).
        if (sampulLuar) gambarTepiSampul(c, w, h, d) // tutup buku: bingkai kayu + blok tebal halaman di sisi luar
        else gambarBayanganTepi(c, w, h, d)
        gambarJilid(c, w, h, d)
        if (kunci.isNotEmpty()) synchronized(cacheBalik) { cacheBalik[k] = hasil }
        return if (kunci.isNotEmpty()) (hasil.copy(Bitmap.Config.ARGB_8888, false) ?: hasil) else hasil
    }

    private fun hiasiDepan(c: Canvas, w: Int, h: Int, jenis: Int, d: Float, gelap: Boolean) {
        if (jenis == HALAMAN) {
            gambarTepiHalaman(c, w, h, d, gelap)
            gambarLabel(c, w, h, d)
        } else {
            gambarTepiSampul(c, w, h, d)
        }
        gambarJilid(c, w, h, d)
    }

    /** Pin penanda ala 📌 (digambar sendiri, tidak bergantung font emoji). (x,y) = kiri-atas kotak 26dp. */
    private fun gambarPin(c: Canvas, x: Float, y: Float, d: Float) {
        c.save()
        c.translate(x + 13f * d, y + 13f * d)
        c.rotate(35f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // bayangan
        p.color = Color.argb(60, 0, 0, 0)
        c.drawCircle(1.5f * d, 1.8f * d, 6.2f * d, p)
        // jarum
        p.color = Color.parseColor("#7A7A7A")
        val jarum = Path().apply {
            moveTo(-0.9f * d, 0f); lineTo(0.9f * d, 0f); lineTo(0f, 13f * d); close()
        }
        c.drawPath(jarum, p)
        // kepala
        p.color = Color.parseColor("#B71C1C"); c.drawCircle(0f, -4.5f * d, 6.4f * d, p)
        p.color = Color.parseColor("#E53935"); c.drawCircle(0f, -4.8f * d, 5.5f * d, p)
        p.color = Color.argb(215, 255, 138, 128); c.drawOval(RectF(-3.6f * d, -8.8f * d, -0.6f * d, -6.4f * d), p)
        p.color = Color.parseColor("#8E1B1B"); c.drawRect(-4.8f * d, 0.6f * d, 4.8f * d, 2.2f * d, p)
        c.restore()
    }

    // ------------------------------------------------------------------ jilid spiral
    private fun gambarJilid(c: Canvas, w: Int, h: Int, d: Float) {
        // bayangan lipatan di sisi jilid
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 24f * d, 0f,
            intArrayOf(Color.argb(95, 40, 20, 5), Color.argb(0, 40, 20, 5)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 24f * d, h.toFloat(), p)
        p.shader = null

        val pitch = 22f * d
        val jumlah = ((h - 20f * d) / pitch).toInt().coerceAtLeast(1)
        val awal = (h - (jumlah - 1) * pitch) / 2f
        val lubang = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#26160B") }
        val rimLubang = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 230, 180); style = Paint.Style.STROKE; strokeWidth = 0.8f * d
        }
        val bayang = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 20, 8, 0); style = Paint.Style.STROKE
            strokeWidth = 3.2f * d; strokeCap = Paint.Cap.ROUND
        }
        val kawat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.8f * d; strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(0f, 0f, 18f * d, 0f,
                intArrayOf(Color.parseColor("#8A5A1E"), Color.parseColor("#E8BF6A"), Color.parseColor("#A87A32")),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        val kilau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 240, 200); style = Paint.Style.STROKE
            strokeWidth = 0.9f * d; strokeCap = Paint.Cap.ROUND
        }
        val hx = 13f * d
        val jalur = Path()
        for (i in 0 until jumlah) {
            val y = awal + i * pitch
            c.drawOval(RectF(hx - 3.8f * d, y - 3.2f * d, hx + 3.8f * d, y + 3.2f * d), lubang)
            c.drawOval(RectF(hx - 3.8f * d, y - 3.2f * d, hx + 3.8f * d, y + 3.2f * d), rimLubang)
            jalur.reset()
            jalur.moveTo(-1f * d, y + 4.2f * d)
            jalur.cubicTo(5f * d, y - 7f * d, 11f * d, y - 6.5f * d, hx + 3f * d, y + 0.5f * d)
            c.save(); c.translate(0.9f * d, 1.3f * d); c.drawPath(jalur, bayang); c.restore()
            c.drawPath(jalur, kawat)
            c.save(); c.translate(0f, -0.9f * d); c.drawPath(jalur, kilau); c.restore()
        }
    }

    private fun gambarBayanganTepi(c: Canvas, w: Int, h: Int, d: Float) {
        // sisi luar halaman balik: garis tipis tepi kertas
        val p = Paint().apply { color = Color.argb(70, 120, 90, 50); strokeWidth = 1f * d }
        c.drawLine(w - 0.5f * d, 0f, w - 0.5f * d, h.toFloat(), p)
    }

    // ------------------------------------------------------------------ tepi
    private fun gambarTepiHalaman(c: Canvas, w: Int, h: Int, d: Float, gelap: Boolean = false) {
        // EFEK TUMPUKAN BUKU TEBAL: dari luar ke dalam di sisi kanan & bawah =
        // celah 1dp (latar gelap spy tumpukan terlihat) -> sampul kayu -> 6 lembar bergantian krem/tan.
        val catCelah = Paint().apply { color = Color.parseColor("#1C1109") }
        val wood = Paint().apply { color = kayu }
        val lembar = Paint()
        val cream = Color.parseColor(if (gelap) "#4A4034" else "#F4EBD6")
        val tan = Color.parseColor(if (gelap) "#352D24" else "#CDBB98")
        val celah = 1f * d; val sampul = 2.5f * d; val tebalLembar = 1.5f * d
        val jumlah = 6

        // atas: bingkai kayu tipis saja
        c.drawRect(0f, 0f, w.toFloat(), 2.5f * d, wood)

        // KANAN
        var x = w.toFloat()
        c.drawRect(x - celah, 0f, x, h.toFloat(), catCelah); x -= celah
        c.drawRect(x - sampul, 0f, x, h.toFloat(), wood); x -= sampul
        for (k in 0 until jumlah) {
            lembar.color = if (k % 2 == 0) cream else tan
            c.drawRect(x - tebalLembar, 2.5f * d, x, h.toFloat(), lembar); x -= tebalLembar
        }
        // BAWAH
        var y = h.toFloat()
        c.drawRect(0f, y - celah, w.toFloat(), y, catCelah); y -= celah
        c.drawRect(0f, y - sampul, w.toFloat(), y, wood); y -= sampul
        for (k in 0 until jumlah) {
            lembar.color = if (k % 2 == 0) cream else tan
            c.drawRect(0f, y - tebalLembar, w - (celah + sampul), y, lembar); y -= tebalLembar
        }
        // bayangan halus di bawah lembar teratas (kesan berlapis)
        val bayang = Paint().apply {
            shader = LinearGradient(x - 4f * d, 0f, x, 0f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(45, 60, 40, 15)), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(x - 4f * d, 2.5f * d, x, y, bayang)
    }

    private fun gambarTepiSampul(c: Canvas, w: Int, h: Int, d: Float) {
        val wood = Paint().apply { color = Color.parseColor("#2A1810") }
        c.drawRect(0f, 0f, w.toFloat(), 3f * d, wood)
        c.drawRect(0f, h - 3f * d, w.toFloat(), h.toFloat(), wood)
        c.drawRect(w - 3f * d, 0f, w.toFloat(), h.toFloat(), wood)
        // blok tumpukan halaman di sisi kanan sampul (terlihat seperti ketebalan buku)
        val kiri = w - 11f * d; val kanan = w - 3f * d
        val atas = 7f * d; val bawah = h - 7f * d
        val sh = Paint().apply {
            shader = LinearGradient(kiri - 7f * d, 0f, kiri, 0f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(110, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(kiri - 7f * d, atas + 3f * d, kiri, bawah, sh)
        c.drawRect(kiri, atas, kanan, bawah, Paint().apply { color = Color.parseColor("#FFFDF7") })
        val g = Paint().apply { color = Color.parseColor("#DCCDB0"); strokeWidth = 0.8f * d }
        var x = kiri + 1.6f * d
        while (x < kanan) { c.drawLine(x, atas, x, bawah, g); x += 1.6f * d }
        c.drawRect(kiri, atas, kanan, atas + 1.2f * d, Paint().apply { color = Color.parseColor("#E8DCC3") })
    }

    // ------------------------------------------------------------------ label pojok
    private fun gambarLabel(c: Canvas, w: Int, h: Int, d: Float) {
        val teks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 168, 118, 44); textSize = 10f * d
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC); textAlign = Paint.Align.RIGHT
        }
        val xKanan = w - 22f * d; val y = h - 20f * d
        c.drawText("Pustaka FK", xKanan, y, teks)
        val lebar = teks.measureText("Pustaka FK")
        val biji = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 3) {
            val cx = xKanan - lebar - 7f * d - i * 5.2f * d
            biji.shader = android.graphics.RadialGradient(cx - 0.6f * d, y - 4.2f * d, 3f * d,
                intArrayOf(Color.parseColor("#E8BF6A"), Color.parseColor("#A87A32")), null, Shader.TileMode.CLAMP)
            biji.alpha = 190
            c.drawCircle(cx, y - 3.2f * d, 2.1f * d, biji)
        }
    }

    // ------------------------------------------------------------------ kertas balik
    /** Kertas bergaris + watermark cincin tasbih "PUSTAKA FK" (terbaca normal; belum dibalik). */
    fun buatKertasBalik(w: Int, h: Int, d: Float, gelap: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor(if (gelap) "#1B1612" else "#F6EEDC"))
        val garis = Paint().apply { color = Color.parseColor(if (gelap) "#272019" else "#E3D7BF"); strokeWidth = 1f * d }
        var y = 28f * d
        while (y < h) { c.drawLine(0f, y, w.toFloat(), y, garis); y += 28f * d }

        val cx = w * 0.52f; val cy = h * 0.46f
        val r = min(w, h) * 0.30f
        val biji = Paint(Paint.ANTI_ALIAS_FLAG)
        val n = 36
        for (i in 0 until n) {
            val a = 2 * PI * i / n
            val bx = cx + r * cos(a).toFloat(); val by = cy + r * sin(a).toFloat()
            val br = r * 0.062f * (if (i % 9 == 0) 1.5f else 1f)
            biji.shader = android.graphics.RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.4f,
                if (gelap) intArrayOf(Color.argb(110, 201, 166, 107), Color.argb(80, 150, 112, 60)) else intArrayOf(Color.argb(95, 150, 105, 60), Color.argb(70, 95, 62, 32)), null, Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br, biji)
        }
        biji.shader = null
        val lingkar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = Color.argb(70, 150, 105, 60)
        }
        c.drawCircle(cx, cy, r * 0.80f, lingkar)
        c.drawCircle(cx, cy, r * 0.74f, lingkar)

        val judul = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (gelap) Color.argb(120, 201, 166, 107) else Color.argb(95, 90, 58, 30); textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC); textSize = r * 0.34f
        }
        c.drawText("Pustaka FK", cx, cy + r * 0.04f, judul)
        judul.textSize = r * 0.15f; judul.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        judul.color = Color.argb(85, 150, 105, 60)
        c.drawText("Arsip Fatwa & Kehidupan", cx, cy + r * 0.30f, judul)
        // garis pemisah kecil
        c.drawLine(cx - r * 0.25f, cy + r * 0.12f, cx + r * 0.25f, cy + r * 0.12f,
            Paint().apply { color = Color.argb(80, 150, 105, 60); strokeWidth = 1f * d })
        return bmp
    }
}
