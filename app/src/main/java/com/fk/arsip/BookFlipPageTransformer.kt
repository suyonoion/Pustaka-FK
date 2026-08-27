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
 * CATATAN JUJUR: ini BUKAN replika piksel-demi-piksel dari curl OpenGL
 * (fi.harism.curl / PageCurl_BookView) yang membengkokkan halaman menjadi
 * lengkungan silinder mengikuti sentuhan. Ini memutar halaman sebagai bidang
 * datar kaku pada sumbu Y, disertai bayangan gradasi di sisi lipatan supaya
 * terasa seperti "buku dibalik". Karena tidak bisa diuji langsung di
 * perangkat fisik dari sini, arah rotasi (rotationY) mungkin perlu dibalik
 * tandanya (lihat komentar TUNE di bawah) kalau visualnya terasa terbalik
 * saat dites di HP.
 *
 * Cara pakai:
 *   proyektorBuku.setPageTransformer(BookFlipPageTransformer())
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    // TUNE: seberapa jauh halaman berikutnya "mengintip" sebelum benar-benar
    // terlihat penuh. 0f = halaman berikutnya langsung diam persis di posisi
    // akhir (murni tertutup halaman lama yang berotasi pergi).
    private val kedalamanBayangan = 160 // 0-255, makin besar makin gelap

    override fun transformPage(page: View, position: Float) {
        val lebarHalaman = page.width
        if (lebarHalaman == 0) return

        // Kamera dijauhkan supaya rotasi terlihat seperti perspektif natural,
        // bukan gepeng. Dikalikan density supaya konsisten di semua ukuran layar.
        page.cameraDistance = 12000f * page.resources.displayMetrics.density

        when {
            position < -1f || position > 1f -> {
                // Di luar jangkauan render, sembunyikan total.
                page.alpha = 0f
            }

            position <= 0f -> {
                // HALAMAN LAMA (sedang aktif / akan ditinggalkan) -- posisi
                // bergerak dari 0 (diam penuh) ke -1 (baru selesai dibalik).
                // Diputar pada sisi KANAN (seolah punggung buku ada di kanan,
                // dibalik ke kiri seperti membaca maju).
                page.alpha = 1f
                page.translationX = 0f
                page.pivotX = lebarHalaman.toFloat()
                page.pivotY = page.height * 0.5f
                // TUNE: kalau arah putaran kebalik di HP, ganti jadi: -90f * position
                page.rotationY = 90f * position
                page.scaleX = 1f
                page.scaleY = 1f
                terapkanBayanganLipatan(page, abs(position))
            }

            else -> {
                // HALAMAN BERIKUTNYA -- posisi bergerak dari 1 (belum kelihatan,
                // masih di slot sebelah kanan) ke 0 (jadi halaman aktif penuh).
                // translationX di sini MENIADAKAN geseran default ViewPager2,
                // supaya halaman berikutnya sudah "diam di tempat" seolah
                // sudah ada di baliknya sejak awal -- baru terlihat progresif
                // saat halaman lama di depannya berputar pergi.
                page.alpha = 1f
                page.translationX = -lebarHalaman * position
                page.rotationY = 0f
                page.pivotX = 0f
                page.pivotY = page.height * 0.5f
                page.scaleX = 1f
                page.scaleY = 1f
                terapkanBayanganLipatan(page, abs(position))
            }
        }
    }

    /**
     * Overlay gradasi gelap tipis di sisi lipatan yang menguat seiring
     * halaman berputar, supaya terasa ada kedalaman/bayangan seperti kertas
     * asli -- bukan sekadar rotasi datar tanpa dimensi.
     * Dipasang lewat View.foreground supaya tidak perlu mengubah layout
     * item_buku.xml maupun BukuAdapter sama sekali.
     */
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
}
