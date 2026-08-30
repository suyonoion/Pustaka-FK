package com.fk.arsip

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Drawable kustom yang menggambar bayangan lipatan berbentuk LENGKUNG dengan
 * pita gelap-terang-gelap (mensimulasikan permukaan silinder yang tergulung),
 * plus sliver tipis "sisi belakang kertas mengintip" tepat di ujung lipatan --
 * supaya kesannya benar-benar tergulung, bukan sekadar dilipat rata.
 *
 * Ditempel sebagai View.foreground pada halaman yang sedang berputar (lihat
 * BookFlipPageTransformer). Drawable foreground murni visual dan tidak ikut
 * menangkap sentuhan, jadi semua View interaktif di baliknya tetap berfungsi.
 */
class CurlShadowDrawable : Drawable() {

    var intensitas: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

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

        val lebarLengkung = min(lebar * (0.14f + 0.34f * intensitas), lebar)
        val xEngsel = lebar // sisi engsel selalu di kanan (pivotX = lebar)
        val xPuncakLengkung = xEngsel - lebarLengkung
        val lekukTengah = lebarLengkung * 0.32f

        // --- Area lengkung tempat pita gelap-terang-gelap digambar ---
        pathBayangan.reset()
        pathBayangan.moveTo(xEngsel, 0f)
        pathBayangan.quadTo(xPuncakLengkung + lekukTengah, tinggi * 0.5f, xEngsel, tinggi)
        pathBayangan.lineTo(xPuncakLengkung, tinggi)
        pathBayangan.quadTo(xPuncakLengkung - lekukTengah, tinggi * 0.5f, xPuncakLengkung, 0f)
        pathBayangan.close()

        canvas.save()
        canvas.clipPath(pathBayangan)

        // PITA GELAP -> TERANG -> GELAP sepanjang lebar lengkungan, meniru
        // cara cahaya jatuh pada permukaan silinder yang tergulung: sisi
        // yang menghadap cahaya (tengah gulungan) paling terang, kedua
        // ujungnya (awal lipatan & bagian yang menekuk ke bawah) gelap.
        catPita.shader = LinearGradient(
            xPuncakLengkung, 0f, xEngsel, 0f,
            intArrayOf(
                0x99000000.toInt(), // ujung jauh dari engsel: gelap (mulai menekuk)
                0x33000000,         // transisi
                0xCCFFFFFF.toInt(), // tengah gulungan: terang (memantulkan cahaya)
                0x22000000,         // transisi
                0xBB000000.toInt()  // tepat di garis engsel: gelap (lipatan terdalam)
            ),
            floatArrayOf(0f, 0.28f, 0.52f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        catPita.alpha = (255 * intensitas).toInt().coerceIn(0, 255)
        canvas.drawRect(xPuncakLengkung, 0f, xEngsel, tinggi, catPita)
        canvas.restore()

        // --- Sliver tipis "sisi belakang kertas" mengintip di ujung lipatan ---
        val lebarSliver = lebarLengkung * 0.10f
        if (lebarSliver > 0.5f) {
            pathSliver.reset()
            pathSliver.moveTo(xEngsel, 0f)
            pathSliver.quadTo(xEngsel - lebarSliver + lekukTengah * 0.2f, tinggi * 0.5f, xEngsel, tinggi)
            pathSliver.lineTo(xEngsel - lebarSliver, tinggi)
            pathSliver.quadTo(xEngsel - lebarSliver - lekukTengah * 0.2f, tinggi * 0.5f, xEngsel - lebarSliver, 0f)
            pathSliver.close()
            catSliver.alpha = (150 * intensitas).toInt().coerceIn(0, 150)
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
