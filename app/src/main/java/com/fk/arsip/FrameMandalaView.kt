package com.fk.arsip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lapisan dekoratif latar layar inisialisasi (Gambar B): bingkai ukiran emas
 * tipis, ornamen bintang-8 di empat sudut + berlian di tengah sisi, dan dua
 * medali kaligrafi samar di kiri/kanan atas. Digambar dengan Canvas (vektor)
 * sehingga skala mengikuti ukuran layar, bukan gambar bitmap yang terpotong.
 */
class FrameMandalaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Label samar "Alat Tasbih" yang ada di mockup Gambar B (bawaan generator gambar). Default mati. */
    var tampilkanLabelAlat: Boolean = false
        set(v) { field = v; invalidate() }

    /** Medali kaligrafi samar di atas kiri/kanan. Matikan utk halaman yang pendek (mis. sampul dalam). */
    var tampilkanMedali: Boolean = true
        set(v) { field = v; invalidate() }

    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    private val emas = Color.parseColor("#D9A94E")
    private val pGaris = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pIsi = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pTeks = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return

        // Bingkai ganda
        garis(c, 90, 1.2f); c.drawRect(dp(10f), dp(10f), w - dp(10f), h - dp(10f), pGaris)
        garis(c, 55, 0.7f); c.drawRect(dp(15f), dp(15f), w - dp(15f), h - dp(15f), pGaris)

        // Sudut
        val sudut = listOf(dp(10f) to dp(10f), (w - dp(10f)) to dp(10f), dp(10f) to (h - dp(10f)), (w - dp(10f)) to (h - dp(10f)))
        for ((i, p) in sudut.withIndex()) {
            val sx = if (i % 2 == 0) 1f else -1f
            val sy = if (i < 2) 1f else -1f
            ornamenSudut(c, p.first, p.second, sx, sy)
        }

        // Berlian di tengah tiap sisi
        berlian(c, w / 2f, dp(10f), dp(5f)); berlian(c, w / 2f, h - dp(10f), dp(5f))
        berlian(c, dp(10f), h / 2f, dp(5f)); berlian(c, w - dp(10f), h / 2f, dp(5f))

        // Medali kaligrafi samar (kiri & kanan atas)
        val ry = dp(108f); val rr = dp(27f)
        if (tampilkanMedali) {
            medali(c, w * 0.21f, ry, rr, "القرآن الكريم")
            medali(c, w * 0.79f, ry, rr, "القرآن الكريم")
        }

        if (tampilkanLabelAlat) {
            pTeks.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            pTeks.color = Color.argb(34, 255, 220, 160)
            pTeks.textSize = dp(46f); c.drawText("Alat Tasbih", w * 0.53f, h * 0.60f, pTeks)
            pTeks.textSize = dp(34f); c.drawText("Alat Tasbih", w * 0.26f, h * 0.90f, pTeks)
        }
    }

    private fun garis(c: Canvas, alpha: Int, tebalDp: Float) {
        pGaris.style = Paint.Style.STROKE
        pGaris.strokeWidth = dp(tebalDp)
        pGaris.color = Color.argb(alpha, Color.red(emas), Color.green(emas), Color.blue(emas))
    }

    private fun berlian(c: Canvas, x: Float, y: Float, r: Float) {
        val p = Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
        pIsi.color = Color.argb(150, Color.red(emas), Color.green(emas), Color.blue(emas))
        c.drawPath(p, pIsi)
    }

    private fun bintang8(x: Float, y: Float, rLuar: Float, rDalam: Float): Path {
        val p = Path()
        for (i in 0 until 16) {
            val a = Math.PI / 8 * i - Math.PI / 2
            val r = if (i % 2 == 0) rLuar else rDalam
            val px = x + (r * cos(a)).toFloat(); val py = y + (r * sin(a)).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close(); return p
    }

    private fun ornamenSudut(c: Canvas, x: Float, y: Float, sx: Float, sy: Float) {
        val cx = x + sx * dp(22f); val cy = y + sy * dp(22f)
        garis(c, 140, 1f)
        c.drawPath(bintang8(cx, cy, dp(17f), dp(9f)), pGaris)
        c.drawCircle(cx, cy, dp(21f), pGaris)
        garis(c, 90, 0.8f)
        c.drawCircle(cx, cy, dp(5f), pGaris)
        // lengan pola ke sepanjang sisi
        for (k in 1..3) {
            val off = dp(22f) + dp(24f) * k + dp(6f)
            berlian(c, x + sx * off, y, dp(3.2f))
            berlian(c, x, y + sy * off, dp(3.2f))
        }
        garis(c, 70, 0.8f)
        c.drawLine(x + sx * dp(46f), y, x + sx * dp(100f), y, pGaris)
        c.drawLine(x, y + sy * dp(46f), x, y + sy * dp(100f), pGaris)
    }

    private fun medali(c: Canvas, x: Float, y: Float, r: Float, teks: String) {
        garis(c, 55, 1f); c.drawCircle(x, y, r, pGaris)
        garis(c, 35, 0.7f); c.drawCircle(x, y, r * 0.86f, pGaris)
        pTeks.typeface = Typeface.DEFAULT_BOLD
        pTeks.color = Color.argb(70, 232, 190, 120)
        pTeks.textSize = r * 0.62f
        val dasar = y - (pTeks.descent() + pTeks.ascent()) / 2f
        c.drawText(teks, x, dasar, pTeks)
    }
}
