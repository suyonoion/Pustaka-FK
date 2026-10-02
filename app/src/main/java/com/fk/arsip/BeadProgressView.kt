package com.fk.arsip

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Progress bar berbentuk untaian biji tasbih (pengganti ProgressBar horizontal).
 * API sengaja dibuat sama dengan ProgressBar (progress, max, isIndeterminate)
 * supaya MainActivity cukup mengganti tipe view-nya.
 *
 * Responsif: jumlah biji dihitung dari lebar view, diameter biji disesuaikan
 * supaya untaian selalu pas memenuhi lebar tanpa sisa.
 */
class BeadProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var max: Int = 100
        set(v) { field = max(1, v); invalidate() }

    var progress: Int = 0
        set(v) { field = v.coerceIn(0, max); invalidate() }

    var isIndeterminate: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            if (v) mulaiAnimasi() else hentikanAnimasi()
            invalidate()
        }

    private val dens = resources.displayMetrics.density
    private val diameterIdealPx = 11.5f * dens
    private var fasa = 0f
    private var animator: ValueAnimator? = null

    private val paintTali = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B170C"); strokeWidth = 2f * dens; style = Paint.Style.STROKE
    }
    private val paintBiji = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintKilau = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onMeasure(w: Int, h: Int) {
        val lebar = MeasureSpec.getSize(w)
        val tinggi = (diameterIdealPx * 2.2f).toInt()
        setMeasuredDimension(lebar, resolveSize(tinggi, h))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isIndeterminate) mulaiAnimasi()
    }

    override fun onDetachedFromWindow() {
        hentikanAnimasi()
        super.onDetachedFromWindow()
    }

    private fun mulaiAnimasi() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { fasa = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun hentikanAnimasi() {
        animator?.cancel(); animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val lebar = width.toFloat()
        if (lebar <= 0f) return
        val jumlah = max(8, (lebar / diameterIdealPx).toInt())
        val d = lebar / jumlah
        val r = d / 2f * 0.92f
        val cy = height / 2f

        canvas.drawLine(r, cy, lebar - r, cy, paintTali)

        val menyala = if (isIndeterminate) 0 else (progress.toFloat() / max * jumlah).roundToInt()
        val pusatKilat = fasa * (jumlah + 6) - 3f

        for (i in 0 until jumlah) {
            val cx = d * i + d / 2f
            val nyalaBiasa = i < menyala
            val jarakKilat = if (isIndeterminate) kotlin.math.abs(i - pusatKilat) else 99f
            val kilat = isIndeterminate && jarakKilat < 3.2f
            if (nyalaBiasa || kilat) {
                val t = if (kilat) 1f - jarakKilat / 3.2f
                        else (i.toFloat() / max(1, menyala - 1)).coerceIn(0f, 1f)
                gambarBijiMenyala(canvas, cx, cy, r, if (kilat) 0.55f + 0.45f * t else 0.35f + 0.65f * t)
            } else {
                gambarBijiKayu(canvas, cx, cy, r)
            }
        }
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()
    private fun campur(c1: Int, c2: Int, t: Float) = Color.rgb(
        lerp(Color.red(c1), Color.red(c2), t),
        lerp(Color.green(c1), Color.green(c2), t),
        lerp(Color.blue(c1), Color.blue(c2), t)
    )

    private fun gambarBijiKayu(c: Canvas, x: Float, y: Float, r: Float) {
        paintBiji.style = Paint.Style.FILL
        paintBiji.shader = RadialGradient(
            x - r * 0.35f, y - r * 0.4f, r * 1.4f,
            intArrayOf(Color.parseColor("#8A5E3E"), Color.parseColor("#4E3020"), Color.parseColor("#26140A")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, paintBiji)
        paintBiji.shader = null
        paintKilau.alpha = 60
        c.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.22f, paintKilau)
    }

    private fun gambarBijiMenyala(c: Canvas, x: Float, y: Float, r: Float, kecerahan: Float) {
        val tepi = campur(Color.parseColor("#7A3410"), Color.parseColor("#E8731C"), kecerahan)
        val tengah = campur(Color.parseColor("#B8651F"), Color.parseColor("#FFC274"), kecerahan)
        val inti = campur(Color.parseColor("#E8A560"), Color.parseColor("#FFF0CF"), kecerahan)
        paintGlow.shader = RadialGradient(
            x, y, r * 2.4f,
            intArrayOf(Color.argb((110 * kecerahan).toInt(), 255, 150, 40), Color.argb(0, 255, 150, 40)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r * 2.4f, paintGlow)
        paintBiji.style = Paint.Style.FILL
        paintBiji.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.35f, r * 1.3f,
            intArrayOf(inti, tengah, tepi), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, paintBiji)
        paintBiji.shader = null
        paintKilau.alpha = 150
        c.drawCircle(x - r * 0.32f, y - r * 0.38f, r * 0.2f, paintKilau)
    }
}
