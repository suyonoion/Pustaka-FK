package com.fk.arsip

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.Color

/**
 * Latar berbentuk biji tasbih untuk timeline.
 *  - KAYU    : biji bulat kayu (bulan); [seri] 0/1 memberi variasi warna selang-seling.
 *  - MENYALA : biji bulat emas menyala (bulan terpilih).
 *  - RUNCING : biji agak runcing di ujung atas & bawah (tahun), berbingkai emas.
 */
class ManikDrawable(private val gaya: Gaya, private val seri: Int = 0) : Drawable() {

    enum class Gaya { KAYU, MENYALA, RUNCING }

    private val dens = Resources.getSystem().displayMetrics.density
    private val pIsi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGaris = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val jalur = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val inset = 1.5f * dens
        val l = b.left + inset; val t = b.top + inset; val r = b.right - inset; val bt = b.bottom - inset
        val w = r - l; val h = bt - t
        val cx = (l + r) / 2f; val cy = (t + bt) / 2f

        val (inti, tengah, tepi) = when (gaya) {
            Gaya.KAYU -> if (seri == 0) Triple("#D2AE86", "#B3834F", "#84583A") else Triple("#C8A27A", "#A97A48", "#7C5232")
            Gaya.MENYALA -> Triple("#FFE2A8", "#F0A040", "#B05A1C")
            Gaya.RUNCING -> Triple("#C09566", "#946336", "#664226")
        }
        pIsi.style = Paint.Style.FILL
        pIsi.shader = RadialGradient(cx - w * 0.2f, cy - h * 0.25f, maxOf(w, h) * 0.85f,
            intArrayOf(Color.parseColor(inti), Color.parseColor(tengah), Color.parseColor(tepi)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)

        jalur.reset()
        if (gaya == Gaya.RUNCING) {
            jalur.moveTo(cx, t)
            jalur.cubicTo(cx + w * 0.30f, t + h * 0.12f, r, cy - h * 0.30f, r, cy)
            jalur.cubicTo(r, cy + h * 0.30f, cx + w * 0.30f, bt - h * 0.12f, cx, bt)
            jalur.cubicTo(cx - w * 0.30f, bt - h * 0.12f, l, cy + h * 0.30f, l, cy)
            jalur.cubicTo(l, cy - h * 0.30f, cx - w * 0.30f, t + h * 0.12f, cx, t)
            jalur.close()
        } else {
            jalur.addOval(RectF(l, t, r, bt), Path.Direction.CW)
        }
        c.drawPath(jalur, pIsi)
        pIsi.shader = null

        // serat kayu halus
        if (gaya != Gaya.MENYALA) {
            c.save(); c.clipPath(jalur)
            pGaris.strokeWidth = 0.8f * dens; pGaris.color = Color.argb(40, 60, 30, 10)
            for (k in -2..2) c.drawLine(l, cy + k * h * 0.2f, r, cy + k * h * 0.2f + h * 0.12f, pGaris)
            c.restore()
        }

        // bingkai
        pGaris.strokeWidth = (if (gaya == Gaya.RUNCING) 1.4f else 1f) * dens
        pGaris.color = when (gaya) {
            Gaya.RUNCING -> Color.parseColor("#D2A24C")
            Gaya.MENYALA -> Color.parseColor("#6B3A12")
            else -> Color.parseColor("#6B4A2E")
        }
        c.drawPath(jalur, pGaris)

        // kilau
        pIsi.color = Color.argb(if (gaya == Gaya.MENYALA) 130 else 70, 255, 255, 255)
        c.drawOval(RectF(cx - w * 0.33f, t + h * 0.10f, cx - w * 0.07f, t + h * 0.28f), pIsi)
    }

    override fun setAlpha(alpha: Int) { pIsi.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { pIsi.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
