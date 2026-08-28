package com.fk.arsip

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * PageTransformer yang mensimulasikan efek "halaman buku dibalik" (page-flip
 * 3D) murni memakai transformasi View bawaan Android (rotationY + cameraDistance
 * untuk kesan perspektif, plus overlay gradient sebagai bayangan lipatan) --
 * TANPA OpenGL, TANPA library eksternal, dan konten tiap halaman tetap berupa
 * View hidup (teks tetap bisa di-scroll/select seperti biasa).
 *
 * BUG YANG SUDAH DIPERBAIKI (versi sebelumnya): halaman "berikutnya" dipaksa
 * selalu diam menutupi persis posisi halaman aktif dengan alpha penuh --
 * bahkan saat idle/tidak disentuh. RecyclerView di dalam ViewPager2 secara
 * default menggambar child berindeks lebih besar (halaman berikutnya) DI ATAS
 * child berindeks lebih kecil (halaman aktif), sehingga halaman berikutnya
 * itu selalu menimpa halaman aktif secara permanen -- rotasi/flip yang
 * terjadi di baliknya jadi tidak pernah terlihat, yang tampak cuma overlay
 * bayangannya saja secara statis. Diperbaiki dengan translationZ eksplisit:
 * halaman yang sedang berputar (meninggalkan layar) dipaksa selalu di atas,
 * halaman berikutnya dipaksa selalu di bawah -- baru tersingkap progresif
 * saat halaman di atasnya menipis akibat rotasi.
 *
 * CATATAN JUJUR: ini BUKAN replika piksel-demi-piksel dari curl OpenGL
 * (fi.harism.curl / PageCurl_BookView) yang membengkokkan halaman menjadi
 * lengkungan silinder mengikuti sentuhan. Ini memutar halaman sebagai bidang
 * datar kaku pada sumbu Y. Arah rotasi juga belum tentu pas dites langsung
 * di HP -- lihat komentar TUNE di bawah kalau arahnya terasa terbalik.
 *
 * Cara pakai:
 *   proyektorBuku.setPageTransformer(BookFlipPageTransformer())
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    private val kedalamanBayangan = 160 // 0-255, makin besar makin gelap
    private val zHalamanBerputar = 8f   // cukup besar utk menang urutan gambar
    private val zHalamanBerikutnya = 0f

    override fun transformPage(page: View, position: Float) {
        val lebarHalaman = page.width
        if (lebarHalaman == 0) return

        page.cameraDistance = 12000f * page.resources.displayMetrics.density

        when {
            position < -1f || position > 1f -> {
                page.alpha = 0f
            }

            position <= 0f -> {
                // HALAMAN YANG SEDANG BERPUTAR PERGI (aktif -> ditinggalkan).
                // Selalu di-render PALING ATAS supaya rotasinya kelihatan,
                // tidak tertutup halaman berikutnya yang ada di baliknya.
                page.alpha = 1f
                page.translationX = 0f
                page.translationZ = zHalamanBerputar
                page.pivotX = lebarHalaman.toFloat()
                page.pivotY = page.height * 0.5f
                // TUNE: kalau arah putaran kebalik di HP, ganti jadi: -90f * position
                page.rotationY = 90f * position
                page.scaleX = 1f
                page.scaleY = 1f
                terapkanBayanganLipatan(page, abs(position))
            }

            else -> {
                // HALAMAN BERIKUTNYA -- diam terpaku di posisi akhir, TAPI
                // selalu di-render PALING BAWAH (Z rendah). Saat idle
                // (position = 1, tidak disentuh), halaman ini memang tumpang
                // tindih dengan halaman aktif -- tapi karena Z-nya kalah,
                // ia tersembunyi total di baliknya, tidak terlihat sama
                // sekali sampai halaman di atasnya benar-benar berputar
                // pergi (position mendekati 0).
                page.alpha = 1f
                page.translationX = -lebarHalaman * position
                page.translationZ = zHalamanBerikutnya
                page.rotationY = 0f
                page.pivotX = 0f
                page.pivotY = page.height * 0.5f
                page.scaleX = 1f
                page.scaleY = 1f
                // Tidak diberi overlay bayangan sendiri -- ia baru terlihat
                // progresif lewat "penipisan" visual halaman di atasnya yang
                // berotasi, jadi tidak butuh bayangan tambahan di sisinya.
                bersihkanBayangan(page)
            }
        }
    }

    private fun terapkanBayanganLipatan(page: View, intensitas: Float) {
        val overlayLama = page.tag as? GradientDrawable
        val overlay = overlayLama ?: GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00000000, 0xFF000000.toInt())
        ).also {
            page.foreground = it
            page.tag = it
        }
        overlay.alpha = (intensitas * kedalamanBayangan).toInt().coerceIn(0, kedalamanBayangan)
    }

    private fun bersihkanBayangan(page: View) {
        val overlay = page.tag as? GradientDrawable ?: return
        overlay.alpha = 0
    }
}
