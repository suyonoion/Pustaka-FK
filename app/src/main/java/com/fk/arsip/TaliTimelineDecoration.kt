package com.fk.arsip

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

/**
 * Tali tipis di belakang biji timeline. Digambar sebagai ItemDecoration sehingga hanya
 * ada kalau datanya ada (tidak muncul sendirian saat timeline sedang dimuat) dan
 * membentang tepat dari biji paling atas ke paling bawah yang terlihat.
 */
class TaliTimelineDecoration(density: Float) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B89B78")
        strokeWidth = 1f * density
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val n = parent.childCount
        if (n == 0) return
        val atas = parent.getChildAt(0).top.toFloat().coerceAtLeast(0f)
        val bawah = parent.getChildAt(n - 1).bottom.toFloat().coerceAtMost(parent.height.toFloat())
        val x = parent.width / 2f
        c.drawLine(x, atas, x, bawah, paint)
    }
}
