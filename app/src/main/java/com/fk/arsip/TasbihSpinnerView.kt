package com.fk.arsip

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Indikator loading: untaian tasbih berbentuk lingkaran yang berputar, dengan biji guru
 * (lebih besar) + rumbai. Cahaya emas mengejar di sepanjang biji. Pengganti ProgressBar.
 * Animasi hanya jalan saat view terlihat (hemat baterai).
 */
class TasbihSpinnerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dens = resources.displayMetrics.density
    private var frac = 0f
    private var animator: ValueAnimator? = null
    private val jumlah = 11
    private val pRumbai = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#D9A94E")
    }

    override fun onMeasure(w: Int, h: Int) {
        val def = (36 * dens).toInt()
        val lebar = resolveSize(def, w); val tinggi = resolveSize(def, h)
        val s = min(lebar, tinggi)
        setMeasuredDimension(s, s)
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
            duration = 1500L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { frac = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun berhenti() { animator?.cancel(); animator = null }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val rBiji = s * 0.085f
        val radius = s / 2f - rBiji * 1.7f
        val putar = frac * 2f * PI.toFloat()
        val kepala = (frac * 2f) % 1f  // cahaya mengejar 2x lebih cepat dari putaran cincin

        // Rumbai + biji guru pada indeks 0
        val aG = -PI.toFloat() / 2f + putar
        val gx = cx + radius * cos(aG); val gy = cy + radius * sin(aG)
        pRumbai.strokeWidth = s * 0.035f
        val dx = cos(aG); val dy = sin(aG)
        for (k in -1..1) {
            val px = -dy * k * rBiji * 0.55f; val py = dx * k * rBiji * 0.55f
            c.drawLine(gx + dx * rBiji * 0.9f + px, gy + dy * rBiji * 0.9f + py,
                gx + dx * rBiji * 2.6f + px * 1.6f, gy + dy * rBiji * 2.6f + py * 1.6f, pRumbai)
        }

        for (i in 1 until jumlah) {
            val a = -PI.toFloat() / 2f + putar + i * 2f * PI.toFloat() / jumlah
            val posisi = i.toFloat() / jumlah
            var d = (posisi - kepala) % 1f; if (d < 0f) d += 1f
            val nyala = (1f - d * 3.2f).coerceIn(0f, 1f)
            TasbihGfx.biji(c, cx + radius * cos(a), cy + radius * sin(a), rBiji, nyala)
        }
        TasbihGfx.biji(c, gx, gy, rBiji * 1.45f, 0.9f)
    }
}
