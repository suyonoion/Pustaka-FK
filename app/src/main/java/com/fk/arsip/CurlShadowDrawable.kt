package com.fk.arsip

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Drawable kustom yang menggambar bayangan lipatan berbentuk LENGKUNG
 * (pakai Path/Bezier) di dekat sisi engsel halaman -- bukan gradient kotak
 * lurus rata seperti versi sebelumnya. Ini yang membuat kesan "kertas
 * melipat" jauh lebih meyakinkan mendekati curl OpenGL asli, walau tetap
 * bidang datar yang diputar (rotationY), bukan mesh silinder sungguhan.
 *
 * Ditempel sebagai View.foreground pada halaman yang sedang berputar
 * (lihat BookFlipPageTransformer). Karena ini Drawable transparan yang
 * digambar DI ATAS konten, semua View interaktif di baliknya (link, tombol,
 * ImageView foto/video) tetap bisa menerima sentuhan seperti biasa --
 * Drawable foreground murni visual, tidak ikut menangkap event sentuhan.
 *
 * Parameter [intensitas] (0f..1f) mengatur:
 *  - seberapa lebar area lengkungan bayangan dari sisi engsel (kanan),
 *  - seberapa gelap puncak bayangannya,
 *  - posisi & kekuatan garis highlight terang tepat di puncak lengkungan
 *    (simulasi cahaya memantul di tekukan kertas).
 */
class CurlShadowDrawable : Drawable() {

    var intensitas: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    private val catBayangan = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catHighlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathBayangan = Path()

    override fun draw(canvas: Canvas) {
        if (intensitas <= 0.001f) return
        val batas = bounds
        val lebar = batas.width().toFloat()
        val tinggi = batas.height().toFloat()
        if (lebar <= 0f || tinggi <= 0f) return

        // Lebar area lengkungan bayangan: makin besar intensitas (makin
        // jauh berputar), makin lebar area yang "terangkat" dari sisi engsel.
        val lebarLengkung = min(lebar * (0.12f + 0.30f * intensitas), lebar)
        val xEngsel = lebar // sisi engsel selalu di kanan (pivotX = lebar)
        val xPuncakLengkung = xEngsel - lebarLengkung

        // --- Bayangan gelap berbentuk lengkung (bukan kotak lurus) ---
        pathBayangan.reset()
        pathBayangan.moveTo(xEngsel, 0f)
        // Bezier kuadratik: melengkung ke dalam di titik tengah tinggi,
        // meniru profil kertas yang menekuk (bukan garis lurus diagonal).
        pathBayangan.quadTo(xPuncakLengkung + lebarLengkung * 0.35f, tinggi * 0.5f, xEngsel, tinggi)
        pathBayangan.lineTo(xPuncakLengkung, tinggi)
        pathBayangan.quadTo(xPuncakLengkung - lebarLengkung * 0.35f, tinggi * 0.5f, xPuncakLengkung, 0f)
        pathBayangan.close()

        catBayangan.shader = LinearGradient(
            xPuncakLengkung, 0f, xEngsel, 0f,
            intArrayOf(0x00000000, (0x00000000), (0xAA000000.toInt())),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        catBayangan.alpha = (255 * intensitas).toInt().coerceIn(0, 255)
        canvas.drawPath(pathBayangan, catBayangan)

        // --- Highlight terang tepat di puncak lengkungan (efek cahaya) ---
        val xHighlight = xPuncakLengkung + lebarLengkung * 0.15f
        catHighlight.shader = RadialGradient(
            xHighlight, tinggi * 0.5f, lebarLengkung * 0.6f,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP
        )
        catHighlight.alpha = (200 * intensitas).toInt().coerceIn(0, 200)
        canvas.drawRect(xPuncakLengkung, 0f, xEngsel, tinggi, catHighlight)
    }

    override fun setAlpha(alpha: Int) {
        // Alpha keseluruhan sudah diatur lewat properti `intensitas`.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        catBayangan.colorFilter = colorFilter
        catHighlight.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
