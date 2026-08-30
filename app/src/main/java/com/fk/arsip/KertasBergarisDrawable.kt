package com.fk.arsip

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Drawable kustom yang menggambar latar "kertas bergaris" ala buku catatan --
 * garis horizontal tipis berulang tiap [tinggiBarisPx], plus satu garis
 * vertikal merah tipis di dekat sisi kiri (margin ala buku tulis).
 *
 * Dipakai sebagai background item_buku.xml, dipasang lewat kode (bukan XML)
 * di BukuAdapter.onCreateViewHolder karena ini kelas Drawable kustom, bukan
 * resource XML statis. Digambar langsung dengan Canvas -- ringan (cuma garis
 * lurus, tidak ada bitmap yang perlu di-scale/tile) dan tetap tajam di semua
 * kepadatan layar.
 */
class KertasBergarisDrawable(
    private val warnaDasar: Int = 0xFFFFFdF7.toInt(),
    private val warnaGaris: Int = 0xFFD8E4E0.toInt(),
    private val warnaMargin: Int = 0xFFE8B4B0.toInt(),
    tinggiBarisDp: Float = 28f,
    private val density: Float = 1f
) : Drawable() {

    private val tinggiBarisPx = tinggiBarisDp * density
    private val marginKiriPx = 40f * density

    private val catDasar = Paint().apply { color = warnaDasar; style = Paint.Style.FILL }
    private val catGaris = Paint().apply {
        color = warnaGaris
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val catMargin = Paint().apply {
        color = warnaMargin
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    override fun draw(canvas: Canvas) {
        val batas = bounds
        canvas.drawRect(batas, catDasar)

        var y = tinggiBarisPx
        while (y < batas.height()) {
            canvas.drawLine(0f, y, batas.width().toFloat(), y, catGaris)
            y += tinggiBarisPx
        }

        canvas.drawLine(marginKiriPx, 0f, marginKiriPx, batas.height().toFloat(), catMargin)
    }

    override fun setAlpha(alpha: Int) {
        catDasar.alpha = alpha
        catGaris.alpha = alpha
        catMargin.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        catDasar.colorFilter = colorFilter
        catGaris.colorFilter = colorFilter
        catMargin.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE"))
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
