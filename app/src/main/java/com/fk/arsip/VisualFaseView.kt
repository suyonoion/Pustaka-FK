package com.fk.arsip

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Nomor fase 1..6 (0 = koneksi buruk) untuk VisualFaseView. */
fun FaseInjeksi.nomor(): Int = when (this) {
    FaseInjeksi.FASE_1 -> 1
    FaseInjeksi.FASE_2 -> 2
    FaseInjeksi.FASE_3 -> 3
    FaseInjeksi.FASE_4 -> 4
    FaseInjeksi.FASE_5 -> 5
    FaseInjeksi.FASE_6 -> 6
    else -> 0
}

/**
 * Visual bergerak tiap fase: kitab emas bercahaya dikelilingi untaian tasbih yang berputar
 * (biji di belakang kitab digambar sebelum kitab, biji di depan sesudahnya => efek 3D),
 * dengan emblem fase (gambar lama yang sudah diwarnai ulang) yang bergerak sesuai fase:
 *  1 melayang, 2 bergeser menghubungkan, 3 turun masuk ke kitab (unduh), 4 berdenyut (bongkar),
 *  5 biji jatuh ke kitab (injeksi), 6 membesar & bersinar (selesai), 0 bergetar (koneksi buruk).
 * Seluruh ukuran proporsional terhadap lebar view, jadi responsif.
 */
class VisualFaseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dens = resources.displayMetrics.density
    private val buku = ContextCompat.getDrawable(context, R.drawable.img_buku_cahaya)
    private var emblem = ContextCompat.getDrawable(context, R.drawable.img_1_persiapan)?.mutate()
    private var nomor = 1
    private var t = 0f
    private var animator: ValueAnimator? = null
    private val pKilau = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setFase(idGambar: Int, nomorFase: Int) {
        emblem = ContextCompat.getDrawable(context, idGambar)?.mutate()
        nomor = nomorFase
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val lebar = MeasureSpec.getSize(w)
        setMeasuredDimension(lebar, (lebar * 0.66f).toInt())
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); atur() }
    override fun onDetachedFromWindow() { berhenti(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility); atur()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility); atur()
    }

    private fun atur() { if (isAttachedToWindow && isShown) mulai() else berhenti() }

    private fun mulai() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun berhenti() { animator?.cancel(); animator = null }

    private fun putaranCincin() = when (nomor) { 2 -> 2; 4 -> 3; 5 -> 2; else -> 1 }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val dua = 2f * PI.toFloat()
        val s = sin(dua * t)

        // --- cincin tasbih (elips miring) ---
        val n = 32
        val rx = w * 0.47f; val ry = w * 0.085f; val cyRing = h * 0.74f
        val rBase = w * 0.026f
        val putar = dua * t * putaranCincin()
        val chase = (t * 2f) % 1f

        fun gambarBiji(depan: Boolean) {
            for (i in 0 until n) {
                val a = putar + i * dua / n
                val sy = sin(a)
                if ((sy >= 0f) != depan) continue
                val depth = (sy + 1f) / 2f
                val r = rBase * (0.72f + 0.38f * depth)
                var d = (i.toFloat() / n - chase) % 1f; if (d < 0f) d += 1f
                val nyala = if (nomor == 6) 0.9f else (1f - d * 5f).coerceIn(0f, 1f)
                TasbihGfx.biji(c, cx + rx * cos(a), cyRing + ry * sy, r, nyala, (150 + 105 * depth).toInt())
            }
        }

        // cahaya latar
        pKilau.shader = android.graphics.RadialGradient(cx, h * 0.62f, w * 0.5f,
            intArrayOf(android.graphics.Color.argb(if (nomor == 6) 120 else 70, 255, 190, 90), android.graphics.Color.argb(0, 255, 190, 90)),
            floatArrayOf(0f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(cx, h * 0.62f, w * 0.5f, pKilau)
        pKilau.shader = null

        gambarBiji(false)

        // --- kitab ---
        buku?.let {
            val bw = w * 0.62f; val bh = bw * 250f / 420f
            val bob = s * 2f * dens
            val top = h * 0.97f - bh + bob
            it.setBounds((cx - bw / 2f).toInt(), top.toInt(), (cx + bw / 2f).toInt(), (top + bh).toInt())
            it.draw(c)
        }

        // --- emblem fase ---
        emblem?.let { e ->
            val iw = e.intrinsicWidth.coerceAtLeast(1).toFloat(); val ih = e.intrinsicHeight.coerceAtLeast(1).toFloat()
            val sk = minOf(w * 0.46f / iw, h * 0.34f / ih)
            var ew = iw * sk; var eh = ih * sk
            var ex = cx; var ey = h * 0.25f
            var alpha = 255; var rot = 0f
            when (nomor) {
                1 -> ey += s * 4f * dens
                2 -> ex += s * w * 0.10f
                3 -> {
                    ey += t * h * 0.22f
                    alpha = if (t < 0.7f) 255 else (255 * (1f - t) / 0.3f).toInt()
                }
                4 -> { val k = 1f + 0.10f * s; ew *= k; eh *= k; rot = 4f * cos(dua * t) }
                5 -> ey += s * 3f * dens
                6 -> { val k = 1f + 0.08f * sin(2f * dua * t); ew *= k; eh *= k }
                0 -> { ex += sin(dua * t * 8f) * 3f * dens; alpha = 200 }
            }
            e.alpha = alpha.coerceIn(0, 255)
            e.setBounds((ex - ew / 2f).toInt(), (ey - eh / 2f).toInt(), (ex + ew / 2f).toInt(), (ey + eh / 2f).toInt())
            c.save(); c.rotate(rot, ex, ey); e.draw(c); c.restore()
        }

        // --- biji jatuh ke kitab (fase 5) ---
        if (nomor == 5) {
            for (k in 0 until 4) {
                val f = (t * 2f + k * 0.25f) % 1f
                val x = cx + (k - 1.5f) * w * 0.07f
                val y = h * 0.34f + f * h * 0.30f
                TasbihGfx.biji(c, x, y, rBase * 0.9f, 1f - f * 0.5f, (255 * (1f - f * f)).toInt())
            }
        }

        gambarBiji(true)

        // --- percikan cahaya naik ---
        pKilau.color = android.graphics.Color.rgb(255, 226, 150)
        for (k in 0 until 14) {
            val f = (t * (1 + k % 2) + k * 0.071f) % 1f
            val x = cx + sin(k * 12.9898f) * w * 0.17f
            val y = h * 0.60f - f * h * 0.5f
            pKilau.alpha = (220 * sin(PI.toFloat() * f)).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, (1.1f + (k % 3) * 0.5f) * dens, pKilau)
        }
    }
}
