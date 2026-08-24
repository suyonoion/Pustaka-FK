package com.fk.arsip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class TasbihConnectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var totalLangkah: Int = 6
        set(value) { field = value; requestLayout(); invalidate() }

    var langkahAktif: Int = 1
        set(value) { field = value; invalidate() }

    var tinggiBarisPx: Float = 0f
        set(value) { field = value; requestLayout(); invalidate() }

    private val amplitudoOffset = dpToPx(14f)
    private val radiusBead = dpToPx(9f)

    private val paintTali = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val paintBeadGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBeadInti = Paint(Paint.ANTI_ALIAS_FLAG)

    private val warnaTaliGelap = Color.parseColor("#4E342E")
    private val warnaTaliNyala = Color.parseColor("#C68A2E")
    private val warnaBeadIntiNyala = Color.parseColor("#FFB300")
    private val warnaBeadIntiSelesai = Color.parseColor("#EEDC9A")
    private val warnaBeadIntiGelap = Color.parseColor("#5A4A3F")
    private val warnaBeadTepiGelap = Color.parseColor("#3E2723")
    private val warnaBeadTepiGelapInaktif = Color.parseColor("#2E2E2E")

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    /** Offset horizontal (px) untuk baris ke-index (0-based). Dipanggil juga oleh Activity agar lingkaran nomor menempel di jalur kurva. */
    fun offsetXUntukBaris(index: Int): Float = amplitudoOffset * sin(index * 0.85f)

    private fun yUntukBaris(index: Int): Float = tinggiBarisPx * index + tinggiBarisPx / 2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val tinggiTotal = (tinggiBarisPx * totalLangkah).toInt()
        val lebar = MeasureSpec.getSize(widthMeasureSpec)
        val tinggiFinal = if (tinggiTotal > 0) tinggiTotal else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(lebar, tinggiFinal)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalLangkah <= 0 || tinggiBarisPx <= 0f) return

        val pusatX = width / 2f

        // Jalur dasar (gelap) menyambungkan seluruh titik bead
        val jalurGelap = Path()
        for (i in 0 until totalLangkah) {
            val x = pusatX + offsetXUntukBaris(i)
            val y = yUntukBaris(i)
            if (i == 0) {
                jalurGelap.moveTo(x, y)
            } else {
                val xPrev = pusatX + offsetXUntukBaris(i - 1)
                val yPrev = yUntukBaris(i - 1)
                jalurGelap.quadTo(xPrev, yPrev, (x + xPrev) / 2f, (y + yPrev) / 2f)
                jalurGelap.lineTo(x, y)
            }
        }
        paintTali.color = warnaTaliGelap
        canvas.drawPath(jalurGelap, paintTali)

        // Jalur menyala, hanya sampai baris aktif
        if (langkahAktif > 0) {
            val jalurNyala = Path()
            val batas = langkahAktif.coerceAtMost(totalLangkah)
            for (i in 0 until batas) {
                val x = pusatX + offsetXUntukBaris(i)
                val y = yUntukBaris(i)
                if (i == 0) {
                    jalurNyala.moveTo(x, y)
                } else {
                    val xPrev = pusatX + offsetXUntukBaris(i - 1)
                    val yPrev = yUntukBaris(i - 1)
                    jalurNyala.quadTo(xPrev, yPrev, (x + xPrev) / 2f, (y + yPrev) / 2f)
                    jalurNyala.lineTo(x, y)
                }
            }
            paintTali.color = warnaTaliNyala
            canvas.drawPath(jalurNyala, paintTali)
        }

        // Bead di tiap titik
        for (i in 0 until totalLangkah) {
            val x = pusatX + offsetXUntukBaris(i)
            val y = yUntukBaris(i)
            val nomorLangkah = i + 1
            when {
                nomorLangkah < langkahAktif -> gambarBead(canvas, x, y, warnaBeadIntiSelesai, true)
                nomorLangkah == langkahAktif -> gambarBead(canvas, x, y, warnaBeadIntiNyala, true)
                else -> gambarBead(canvas, x, y, warnaBeadIntiGelap, false)
            }
        }
    }

    private fun gambarBead(canvas: Canvas, x: Float, y: Float, warnaInti: Int, nyala: Boolean) {
        if (nyala) {
            paintBeadGlow.shader = RadialGradient(
                x, y, radiusBead * 2.2f,
                intArrayOf(adjustAlpha(warnaInti, 140), adjustAlpha(warnaInti, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, radiusBead * 2.2f, paintBeadGlow)
        }

        paintBeadInti.style = Paint.Style.FILL
        paintBeadInti.shader = null
        paintBeadInti.color = warnaInti
        canvas.drawCircle(x, y, radiusBead, paintBeadInti)

        paintBeadInti.style = Paint.Style.STROKE
        paintBeadInti.strokeWidth = dpToPx(1f)
        paintBeadInti.color = if (nyala) warnaBeadTepiGelap else warnaBeadTepiGelapInaktif
        canvas.drawCircle(x, y, radiusBead, paintBeadInti)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}