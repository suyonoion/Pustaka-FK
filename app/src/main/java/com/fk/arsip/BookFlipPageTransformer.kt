package com.fk.arsip

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * PageTransformer efek "halaman buku dibalik" (page-flip 3D) -- versi
 * rombak total dengan bayangan lipatan LENGKUNG (CurlShadowDrawable, pakai
 * Path/Bezier) menggantikan gradient kotak lurus, plus bayangan-jatuh
 * (elevation) & scale-down untuk kesan kedalaman.
 *
 * Murni transformasi View bawaan Android -- TANPA OpenGL, TANPA library
 * eksternal. Konten tiap halaman TETAP View hidup sepenuhnya: link, tombol
 * Bagikan, foto/video (Glide) semua tetap bisa disentuh, karena Android
 * menghitung ulang koordinat sentuhan sesuai matriks rotasi/skala View saat
 * dispatch touch event -- rotationY tidak membuat View kehilangan
 * interaktivitasnya.
 *
 * CATATAN JUJUR: ini tetap bidang datar kaku yang diputar (rotationY),
 * bukan mesh silinder sungguhan seperti curl OpenGL asli -- tapi kombinasi
 * bayangan-lengkung + bayangan-jatuh + scale ini dirancang untuk mendekati
 * kesan itu semaksimal mungkin tanpa mengorbankan interaktivitas halaman.
 *
 * Cara pakai:
 *   proyektorBuku.setPageTransformer(BookFlipPageTransformer())
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    private val elevasiMaks = 28f
    private val skalaMinimum = 0.94f
    private val zHalamanBerputar = 8f
    private val zHalamanBerikutnya = 0f

    override fun transformPage(page: View, position: Float) {
        val lebarHalaman = page.width
        if (lebarHalaman == 0) return

        page.cameraDistance = 24000f * page.resources.displayMetrics.density

        when {
            position < -1f || position > 1f -> {
                page.alpha = 0f
                page.elevation = 0f
            }

            position <= 0f -> {
                // HALAMAN YANG SEDANG TERANGKAT & BERPUTAR PERGI.
                val intensitas = abs(position) // 0 (diam) -> 1 (baru selesai berputar)

                page.alpha = 1f
                page.translationX = 0f
                page.translationZ = zHalamanBerputar
                page.pivotX = lebarHalaman.toFloat()
                page.pivotY = page.height * 0.5f
                // TUNE: kalau arah putaran kebalik di HP, ganti jadi: -90f * position
                page.rotationY = 90f * position

                val skala = 1f - (1f - skalaMinimum) * intensitas
                page.scaleX = skala
                page.scaleY = skala

                page.elevation = elevasiMaks * intensitas

                terapkanBayanganLengkung(page, intensitas)
            }

            else -> {
                // HALAMAN BERIKUTNYA -- diam di posisi akhir, di-render di
                // bawah (Z rendah) supaya tersembunyi total di balik halaman
                // aktif sampai halaman itu benar-benar berputar pergi.
                page.alpha = 1f
                page.translationX = -lebarHalaman * position
                page.translationZ = zHalamanBerikutnya
                page.elevation = 0f
                page.rotationY = 0f
                page.pivotX = 0f
                page.pivotY = page.height * 0.5f
                page.scaleX = 1f
                page.scaleY = 1f
                bersihkanBayangan(page)
            }
        }
    }

    private fun terapkanBayanganLengkung(page: View, intensitas: Float) {
        val overlay = (page.tag as? CurlShadowDrawable) ?: CurlShadowDrawable().also {
            page.foreground = it
            page.tag = it
        }
        overlay.intensitas = intensitas
    }

    private fun bersihkanBayangan(page: View) {
        (page.tag as? CurlShadowDrawable)?.intensitas = 0f
    }
}
