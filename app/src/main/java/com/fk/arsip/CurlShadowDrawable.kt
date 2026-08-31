package com.fk.arsip

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Drawable kustom bayangan lipatan: pita gelap-terang-gelap (mensimulasikan
 * cahaya di permukaan silinder tergulung).
 *
 * PERBAIKAN (dari laporan: bentuk lengkung/Bezier yang di-clip sebelumnya
 * malah tampak seperti KONTUR/OUTLINE mencolok -- "seperti kabel/pipa" --
 * bukannya menyamar sebagai gulungan kertas): tepi lengkung yang di-clip
 * tegas menghasilkan GARIS POTONG TAJAM yang justru menarik perhatian
 * sebagai bentuk buatan, bukan bayangan alami. Diperbaiki dengan
 * menghilangkan clip berbentuk lengkung sama sekali -- ilusi "gulungan"
 * sekarang HANYA mengandalkan gradasi gelap-terang-gelap pada pita
 * lurus (tanpa tepi kontur apa pun), yang justru lebih meyakinkan karena
 * menyatu dengan rotasi datar halaman itu sendiri tanpa ada garis asing.
 */
class CurlShadowDrawable(density: Float = 1f) : Drawable() {

    var intensitas: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    private val lebarLengkungPx = 34f * density

    private val catPita = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        if (intensitas <= 0.001f) return
        val batas = bounds
        val lebar = batas.width().toFloat()
        val tinggi = batas.height().toFloat()
        if (lebar <= 0f || tinggi <= 0f) return

        val lebarLengkung = lebarLengkungPx.coerceAtMost(lebar)
        val xEngsel = lebar // sisi engsel selalu di kanan (pivotX = lebar)
        val xMulai = xEngsel - lebarLengkung

        // Pita LURUS (tanpa Path/clip melengkung -- itu penyebab efek
        // "kontur/kabel" sebelumnya). Ilusi gulungan murni dari gradasi
        // gelap-terang-gelap, alpha rendah supaya tetap menyatu/transparan
        // dan tidak menutupi teks di baliknya.
        catPita.shader = LinearGradient(
            xMulai, 0f, xEngsel, 0f,
            intArrayOf(
                0x00000000, // menyatu penuh dengan halaman di sisi jauh
                0x40000000,
                0x55FFFFFF, // pantulan cahaya di tengah
                0x18000000,
                0x50000000  // sedikit gelap tepat di garis engsel
            ),
            floatArrayOf(0f, 0.30f, 0.52f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        catPita.alpha = (255 * intensitas).toInt().coerceIn(0, 255)
        canvas.drawRect(xMulai, 0f, xEngsel, tinggi, catPita)
    }

    override fun setAlpha(alpha: Int) {
        // Alpha keseluruhan sudah diatur lewat properti `intensitas`.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        catPita.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
