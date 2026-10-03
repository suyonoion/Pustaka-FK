package com.fk.arsip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Untaian tasbih vertikal untuk stepper inisialisasi (meniru desain "Gambar B"):
 *  - tali masuk dari tepi kiri atas melalui beberapa biji kayu kecil,
 *  - 6 biji besar bernomor (menyala = langkah tercapai) + biji kayu kecil di antaranya,
 *  - ujung bawah: biji kayu kecil, biji guru memanjang dan rumbai.
 *
 * Seluruh ukuran vertikal dalam dp (selaras dengan item_stepper.xml: tinggi baris
 * ROW_DP, padding atas TOP_PAD_DP). Posisi horizontal proporsional terhadap
 * lebar view, jadi tetap pas di layar sempit maupun lebar.
 */
class TasbihConnectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val ROW_DP = 47f
        const val TOP_PAD_DP = 42f
        const val EKOR_DP = 142f // jarak dari pusat biji terakhir sampai ujung rumbai
        const val POSISI_X_DP = 52f // pusat biji besar dari tepi kiri (dp, tetap => jarak ke label konstan)
    }

    var totalLangkah: Int = 6
        set(value) { field = value; requestLayout(); invalidate() }

    var langkahAktif: Int = 1
        set(value) { field = value; invalidate() }

    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    private val rBesar = dp(14f)
    private val rKecil = dp(10f)

    private val pTali = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#24130A")
    }
    private val pIsi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGaris = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pTeks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textSize = 17f * resources.displayMetrics.scaledDensity
    }

    private fun xPusat() = dp(POSISI_X_DP)
    private fun yBaris(i: Int) = dp(TOP_PAD_DP) + dp(ROW_DP) * i + dp(ROW_DP) / 2f

    override fun onMeasure(w: Int, h: Int) {
        val tinggi = yBaris(totalLangkah - 1) + dp(EKOR_DP)
        setMeasuredDimension(MeasureSpec.getSize(w), tinggi.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || totalLangkah <= 0) return
        val x1 = xPusat()
        val yAkhir = yBaris(totalLangkah - 1)

        // Biji kayu lengkung dari tepi kiri atas menuju biji pertama
        val y1 = yBaris(0)
        val busur = listOf(
            -dp(46f) to -dp(66f), -dp(34f) to -dp(55f), -dp(17f) to -dp(41f), -dp(5f) to -dp(25f)
        ).map { (dx, dy) -> (x1 + dx) to (y1 + dy) }

        // Ekor bawah
        val kecilBawah = x1 + dp(2f) to yAkhir + dp(26f)
        val busurBawah = listOf(
            (x1 - dp(37f)) to (yAkhir + dp(33f)),
            (x1 - dp(17f)) to (yAkhir + dp(44f)),
            (x1 + dp(8f)) to (yAkhir + dp(47f))
        )
        val guruPusat = (x1 + dp(78f)) to (yAkhir + dp(80f))

        // 1) TALI
        val jalur = Path()
        jalur.moveTo(busur[0].first - dp(10f), busur[0].second - dp(10f))
        for (p in busur) jalur.lineTo(p.first, p.second)
        for (i in 0 until totalLangkah) jalur.lineTo(x1, yBaris(i))
        jalur.lineTo(kecilBawah.first, kecilBawah.second)
        jalur.lineTo(guruPusat.first, guruPusat.second)
        canvas.drawPath(jalur, pTali)
        val jalurKiri = Path().apply {
            moveTo(-dp(4f), yAkhir + dp(30f))
            for (p in busurBawah) lineTo(p.first, p.second)
            lineTo(guruPusat.first - dp(30f), guruPusat.second - dp(34f))
        }
        canvas.drawPath(jalurKiri, pTali)

        // 2) BIJI KAYU KECIL
        for (p in busur) bijiKayu(canvas, p.first, p.second, dp(9f), dp(9f))
        for (i in 0 until totalLangkah - 1) bijiKayu(canvas, x1, yBaris(i) + dp(ROW_DP) / 2f, rKecil, rKecil * 0.92f)
        bijiKayu(canvas, kecilBawah.first, kecilBawah.second, rKecil, rKecil * 0.92f)
        for (p in busurBawah) bijiKayu(canvas, p.first, p.second, dp(10f), dp(9f))

        // 3) BIJI GURU + RUMBAI
        gambarGuru(canvas, guruPusat.first, guruPusat.second)

        // 4) BIJI BESAR BERNOMOR
        for (i in 0 until totalLangkah) {
            val nomor = i + 1
            val menyala = nomor <= langkahAktif
            bijiBesar(canvas, x1, yBaris(i), nomor, menyala, nomor == langkahAktif)
        }
    }

    private fun bijiKayu(c: Canvas, x: Float, y: Float, rx: Float, ry: Float) {
        val oval = RectF(x - rx, y - ry, x + rx, y + ry)
        pIsi.style = Paint.Style.FILL
        pIsi.shader = RadialGradient(
            x - rx * 0.35f, y - ry * 0.4f, rx * 1.5f,
            intArrayOf(Color.parseColor("#A07048"), Color.parseColor("#5E3B25"), Color.parseColor("#2B170C")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawOval(oval, pIsi)
        pIsi.shader = null
        // serat kayu
        pGaris.strokeWidth = dp(0.8f); pGaris.color = Color.argb(70, 20, 8, 2)
        c.save(); c.clipPath(Path().apply { addOval(oval, Path.Direction.CW) })
        for (k in -2..2) c.drawLine(x - rx, y + k * ry * 0.38f, x + rx, y + k * ry * 0.38f + ry * 0.25f, pGaris)
        c.restore()
        pGaris.strokeWidth = dp(1f); pGaris.color = Color.parseColor("#22120A")
        c.drawOval(oval, pGaris)
        pIsi.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(x - rx * 0.38f, y - ry * 0.42f, rx * 0.2f, pIsi)
    }

    private fun bijiBesar(c: Canvas, x: Float, y: Float, nomor: Int, menyala: Boolean, aktif: Boolean) {
        if (menyala) {
            val rg = rBesar * (if (aktif) 2.9f else 2.3f)
            pIsi.shader = RadialGradient(
                x, y, rg,
                intArrayOf(Color.argb(if (aktif) 190 else 130, 255, 170, 60), Color.argb(0, 255, 170, 60)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(x, y, rg, pIsi)
            pIsi.shader = RadialGradient(
                x - rBesar * 0.3f, y - rBesar * 0.35f, rBesar * 1.45f,
                intArrayOf(Color.parseColor("#FFE2A8"), Color.parseColor("#F0A040"), Color.parseColor("#B05A1C")),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        } else {
            pIsi.shader = RadialGradient(
                x - rBesar * 0.3f, y - rBesar * 0.35f, rBesar * 1.45f,
                intArrayOf(Color.parseColor("#B2A596"), Color.parseColor("#6E645A"), Color.parseColor("#2F2924")),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        pIsi.style = Paint.Style.FILL
        c.drawCircle(x, y, rBesar, pIsi)
        pIsi.shader = null
        pGaris.strokeWidth = dp(1f)
        pGaris.color = if (menyala) Color.parseColor("#6B3A12") else Color.parseColor("#211C18")
        c.drawCircle(x, y, rBesar, pGaris)
        pIsi.color = Color.argb(if (menyala) 120 else 70, 255, 255, 255)
        c.drawCircle(x - rBesar * 0.38f, y - rBesar * 0.45f, rBesar * 0.2f, pIsi)

        pTeks.color = if (menyala) Color.parseColor("#FFF6DC") else Color.parseColor("#CFC6BA")
        pTeks.setShadowLayer(dp(1.5f), 0f, dp(1f), if (menyala) Color.parseColor("#8A4A12") else Color.parseColor("#1A1612"))
        val dasar = y - (pTeks.descent() + pTeks.ascent()) / 2f
        c.drawText(nomor.toString(), x, dasar, pTeks)
        pTeks.clearShadowLayer()
    }

    private fun gambarGuru(c: Canvas, x: Float, y: Float) {
        val panjang = dp(62f); val lebar = dp(17f)
        c.save()
        c.translate(x, y); c.rotate(45f)
        val badan = RectF(-panjang, -lebar, panjang, lebar)
        pIsi.style = Paint.Style.FILL
        pIsi.shader = LinearGradient(0f, -lebar, 0f, lebar,
            intArrayOf(Color.parseColor("#B07C50"), Color.parseColor("#6A4328"), Color.parseColor("#2E190D")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(badan, lebar, lebar, pIsi)
        pIsi.shader = null
        // alur cincin
        pGaris.strokeWidth = dp(1.2f); pGaris.color = Color.argb(110, 25, 10, 3)
        var k = -panjang + lebar * 1.2f
        while (k < panjang - lebar) { c.drawLine(k, -lebar * 0.9f, k, lebar * 0.9f, pGaris); k += dp(13f) }
        pGaris.strokeWidth = dp(1f); pGaris.color = Color.parseColor("#22120A")
        c.drawRoundRect(badan, lebar, lebar, pGaris)
        pIsi.color = Color.argb(60, 255, 255, 255)
        c.drawRoundRect(RectF(-panjang * 0.8f, -lebar * 0.72f, panjang * 0.7f, -lebar * 0.3f), lebar, lebar, pIsi)
        // leher + bola rumbai di ujung
        pIsi.color = Color.parseColor("#3A2214")
        c.drawRect(panjang - dp(2f), -dp(4f), panjang + dp(10f), dp(4f), pIsi)
        pIsi.shader = RadialGradient(panjang + dp(18f) - dp(3f), -dp(3f), dp(12f),
            intArrayOf(Color.parseColor("#A07048"), Color.parseColor("#4E3020"), Color.parseColor("#24120A")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(panjang + dp(18f), 0f, dp(10f), pIsi)
        pIsi.shader = null
        c.restore()
    }
}
