package com.fk.arsip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Garis pembatas ornamen: garis emas memudar di kiri/kanan, kartus runcing
 * di tengah berisi basmalah, dan berlian kecil di ujung garis.
 */
class OrnamenBismillahView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens
    private val emas = Color.parseColor("#E2B964")

    private val pGaris = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val pIsi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTeks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.parseColor("#E8C77A")
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), resolveSize(dp(30f).toInt(), h))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val cy = height / 2f
        val setengahKartus = dp(52f)
        val ujungKiri = w * 0.12f; val ujungKanan = w * 0.88f
        val cx = w / 2f

        // Garis memudar menuju tengah (kiri & kanan)
        pGaris.shader = LinearGradient(ujungKiri, 0f, cx - setengahKartus, 0f,
            intArrayOf(Color.argb(30, 226, 185, 100), Color.argb(200, 226, 185, 100)), null, Shader.TileMode.CLAMP)
        c.drawLine(ujungKiri, cy, cx - setengahKartus - dp(4f), cy, pGaris)
        pGaris.shader = LinearGradient(cx + setengahKartus, 0f, ujungKanan, 0f,
            intArrayOf(Color.argb(200, 226, 185, 100), Color.argb(30, 226, 185, 100)), null, Shader.TileMode.CLAMP)
        c.drawLine(cx + setengahKartus + dp(4f), cy, ujungKanan, cy, pGaris)
        pGaris.shader = null

        berlian(c, ujungKiri, cy, dp(3f)); berlian(c, ujungKanan, cy, dp(3f))

        // Kartus runcing
        val t = dp(11f)
        val kartus = Path().apply {
            moveTo(cx - setengahKartus - dp(8f), cy)
            lineTo(cx - setengahKartus, cy - t)
            lineTo(cx + setengahKartus, cy - t)
            lineTo(cx + setengahKartus + dp(8f), cy)
            lineTo(cx + setengahKartus, cy + t)
            lineTo(cx - setengahKartus, cy + t)
            close()
        }
        pIsi.color = Color.argb(60, 0, 0, 0); pIsi.style = Paint.Style.FILL
        c.drawPath(kartus, pIsi)
        pGaris.color = Color.argb(190, 226, 185, 100)
        c.drawPath(kartus, pGaris)

        pTeks.textSize = dp(13f)
        val dasar = cy - (pTeks.descent() + pTeks.ascent()) / 2f
        c.drawText("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", cx, dasar, pTeks)
    }

    private fun berlian(c: Canvas, x: Float, y: Float, r: Float) {
        val p = Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
        pIsi.color = Color.argb(200, Color.red(emas), Color.green(emas), Color.blue(emas))
        c.drawPath(p, pIsi)
    }
}
