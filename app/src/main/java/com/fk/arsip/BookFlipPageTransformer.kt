package com.fk.arsip

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * PageTransformer efek "halaman buku dibalik" (page-flip 3D) dengan bayangan
 * gulungan (CurlShadowDrawable) + bayangan-jatuh (elevation) + scale-down.
 *
 * PERBAIKAN (dari laporan: ada bayangan aneh di kiri layar saat pertama kali
 * buka mode baca, sebelum digeser sama sekali): sebelumnya elevation &
 * intensitas bayangan dihitung linear dari abs(position) -- artinya halaman
 * "sebelumnya" yang diam di posisi -1 (rotationY = -90, seharusnya tak
 * terlihat sama sekali karena tegak lurus edge-on) tetap mendapat elevation
 * MAKSIMUM, dan bayangan-jatuhnya tetap ter-render walau halamannya sendiri
 * sudah tak kelihatan -- itulah bayangan aneh yang muncul di pojok layar.
 *
 * Diperbaiki dengan kurva "naik-turun" (fungsi sin, memuncak di tengah
 * putaran ~45 derajat, kembali ke NOL persis saat diam di posisi 0 ATAU -1)
 * untuk elevation & intensitas bayangan -- rotationY sendiri tetap linear
 * penuh 0->90 derajat seperti biasa, hanya efek visual tambahannya yang
 * mengikuti kurva ini.
 *
 * Konten tiap halaman TETAP View hidup sepenuhnya -- link, tombol Bagikan,
 * foto/video semua tetap bisa disentuh walau halaman sedang berputar.
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
                val progresRotasi = abs(position) // 0 (diam) -> 1 (baru selesai berputar)
                // Kurva naik-turun: 0 di kedua ujung (diam), puncak di
                // tengah -- inilah yang menghilangkan bayangan hantu saat
                // halaman idle di posisi -1.
                val intensitasVisual = sin(PI.toFloat() * progresRotasi)

                page.alpha = 1f
                page.translationX = 0f
                page.translationZ = zHalamanBerputar
                page.pivotX = lebarHalaman.toFloat()
                page.pivotY = page.height * 0.5f
                // TUNE: kalau arah putaran kebalik di HP, ganti jadi: -90f * position
                page.rotationY = 90f * position

                val skala = 1f - (1f - skalaMinimum) * intensitasVisual
                page.scaleX = skala
                page.scaleY = skala

                page.elevation = elevasiMaks * intensitasVisual

                terapkanBayanganLengkung(page, intensitasVisual)
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

    private fun terapkanBayanganLengkung(page: View, intensitasVisual: Float) {
        val overlay = (page.tag as? CurlShadowDrawable) ?: CurlShadowDrawable(
            density = page.resources.displayMetrics.density
        ).also {
            page.foreground = it
            page.tag = it
        }
        overlay.intensitas = intensitasVisual
    }

    private fun bersihkanBayangan(page: View) {
        (page.tag as? CurlShadowDrawable)?.intensitas = 0f
    }
}
