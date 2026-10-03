package com.fk.arsip

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/** Gambar biji tasbih bersama (dipakai VisualFaseView & TasbihSpinnerView). UI-thread only. */
object TasbihGfx {
    private val pBiji = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pKilau = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt()
    )

    /** nyala 0 = kayu gelap, 1 = emas menyala. alpha 0..255. */
    fun biji(c: Canvas, x: Float, y: Float, r: Float, nyala: Float = 0f, alpha: Int = 255) {
        val n = nyala.coerceIn(0f, 1f)
        if (n > 0.05f) {
            pGlow.shader = RadialGradient(x, y, r * 2.6f,
                intArrayOf(Color.argb((120 * n * alpha / 255f).toInt(), 255, 160, 50), Color.argb(0, 255, 160, 50)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 2.6f, pGlow)
        }
        val tepi = mix(Color.parseColor("#26140A"), Color.parseColor("#B05A1C"), n)
        val tengah = mix(Color.parseColor("#5E3B25"), Color.parseColor("#F0A040"), n)
        val inti = mix(Color.parseColor("#9A6A44"), Color.parseColor("#FFE2A8"), n)
        pBiji.alpha = alpha
        pBiji.shader = RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.45f,
            intArrayOf(inti, tengah, tepi), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, pBiji)
        pBiji.shader = null
        pKilau.alpha = ((60 + 90 * n) * alpha / 255f).toInt()
        c.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.2f, pKilau)
    }
}
