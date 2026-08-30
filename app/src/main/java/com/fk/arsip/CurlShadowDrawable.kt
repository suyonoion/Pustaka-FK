package com.fk.arsip

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Drawable kustom bayangan lipatan berbentuk lengkung dengan pita
 * gelap-terang-gelap (mensimulasikan permukaan silinder tergulung).
 *
 * PERBAIKAN PENTING (dari laporan: muncul "pipa metalik" solid abu-abu
 * menutupi hampir seluruh halaman saat berputar mendekati 90 derajat):
 * versi sebelumnya membuat lebar pita PROPORSIONAL terhadap lebar halaman
 * (sampai 48%). Masalahnya, rotasi 3D (rotationY) itu TIDAK rata: area
 * dekat sisi engsel (pivot) nyaris tidak mengecil sama sekali walau
 * halaman sudah berputar jauh, sementara area jauh dari engsel mengecil
 * drastis (foreshortening). Karena pita bayangan digambar tepat di dekat
 * pivot, lebarnya yang besar & proporsional tadi jadi mendominasi HAMPIR
 * SELURUH sisa halaman yang masih terlihat begitu rotasi mendekati 90
 * derajat -- itulah kenapa jadi tampak seperti pipa solid menutupi teks.
 *
 * Diperbaiki dengan membuat lebar pita SELALU KECIL & TETAP (dalam dp,
 * lewat parameter [density], bukan persentase lebar halaman) -- karena
 * area dekat pivot nyaris tidak terpengaruh foreshortening, pita
 * berukuran tetap ini akan tetap terlihat tipis & wajar di semua sudut
 * rotasi. Alpha maksimum juga diturunkan supaya tidak terlihat solid.
 */
class CurlShadowDrawable(density: Float = 1f) : Drawable() {

    var intensitas: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    // TUNE: lebar pita bayangan dalam dp -- TETAP, tidak ikut membesar
    // sesuai lebar halaman maupun sudut rotasi. Ini kunci supaya efeknya
    // tidak pernah membengkak jadi "pipa" menutupi teks.
    private val lebarLengkungPx = 34f * density
    private val lebarSliverPx = 5f * density

    private val catPita = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catSliver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5F0E6.toInt() // krem terang, mensimulasikan sisi belakang kertas
    }
    private val pathBayangan = Path()
    private val pathSliver = Path()

    override fun draw(canvas: Canvas) {
        if (intensitas <= 0.001f) return
        val batas = bounds
        val lebar = batas.width().toFloat()
        val tinggi = batas.height().toFloat()
        if (lebar <= 0f || tinggi <= 0f) return

        val lebarLengkung = lebarLengkungPx.coerceAtMost(lebar)
        val xEngsel = lebar // sisi engsel selalu di kanan (pivotX = lebar)
        val xPuncakLengkung = xEngsel - lebarLengkung
        val lekukTengah = lebarLengkung * 0.32f

        pathBayangan.reset()
        pathBayangan.moveTo(xEngsel, 0f)
        pathBayangan.quadTo(xPuncakLengkung + lekukTengah, tinggi * 0.5f, xEngsel, tinggi)
        pathBayangan.lineTo(xPuncakLengkung, tinggi)
        pathBayangan.quadTo(xPuncakLengkung - lekukTengah, tinggi * 0.5f, xPuncakLengkung, 0f)
        pathBayangan.close()

        canvas.save()
        canvas.clipPath(pathBayangan)

        // Alpha puncak diturunkan signifikan (dulu sampai ~0xCC/204) supaya
        // tidak terlihat solid/opak menutupi teks di baliknya.
        catPita.shader = LinearGradient(
            xPuncakLengkung, 0f, xEngsel, 0f,
            intArrayOf(
                0x55000000, // ujung jauh dari engsel: gelap tipis
                0x22000000, // transisi
                0x66FFFFFF, // tengah gulungan: terang (memantulkan cahaya) -- TIDAK solid
                0x1A000000, // transisi
                0x66000000  // tepat di garis engsel: gelap tipis
            ),
            floatArrayOf(0f, 0.28f, 0.52f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        catPita.alpha = (255 * intensitas).toInt().coerceIn(0, 255)
        canvas.drawRect(xPuncakLengkung, 0f, xEngsel, tinggi, catPita)
        canvas.restore()

        if (lebarSliverPx > 0.5f) {
            pathSliver.reset()
            pathSliver.moveTo(xEngsel, 0f)
            pathSliver.quadTo(xEngsel - lebarSliverPx + lekukTengah * 0.2f, tinggi * 0.5f, xEngsel, tinggi)
            pathSliver.lineTo(xEngsel - lebarSliverPx, tinggi)
            pathSliver.quadTo(xEngsel - lebarSliverPx - lekukTengah * 0.2f, tinggi * 0.5f, xEngsel - lebarSliverPx, 0f)
            pathSliver.close()
            catSliver.alpha = (110 * intensitas).toInt().coerceIn(0, 110)
            canvas.drawPath(pathSliver, catSliver)
        }
    }

    override fun setAlpha(alpha: Int) {
        // Alpha keseluruhan sudah diatur lewat properti `intensitas`.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        catPita.colorFilter = colorFilter
        catSliver.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
