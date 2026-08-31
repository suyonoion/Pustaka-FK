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
 * SOAL "GULUNGAN MENGIKUTI SENTUHAN" -- CATATAN JUJUR: ViewPager2.PageTransformer
 * SECARA DESAIN tidak pernah menerima koordinat sentuhan sama sekali -- ia
 * cuma dikasih satu angka `position` (progres geser horizontal 0..1). Tidak
 * ada cara membuatnya tahu jari menyentuh bagian atas/tengah/bawah layar
 * lewat mekanisme ini saja. Curl OpenGL asli bisa mengikuti sentuhan persis
 * karena ia membaca event sentuhan mentah dan me-render ulang tiap frame --
 * arsitektur yang sama sekali berbeda dari PageTransformer.
 *
 * Jalan tengah yang jujur bisa dicapai: [rasioSentuhanY] di-update dari LUAR
 * kelas ini (lewat OnTouchListener terpisah yang dipasang MainActivity ke
 * ViewPager2 -- lihat MainActivity.onCreate) untuk melacak posisi Y sentuhan
 * terakhir, lalu dipakai sebagai pivotY rotasi. Efeknya: titik lipatan
 * condong ke arah mana jari terakhir menyentuh (dekat atas vs dekat bawah
 * layar melipat dari titik berbeda) -- BUKAN curl 2D penuh yang mengikuti
 * jari secara real-time per piksel seperti OpenGL, tapi minimal terasa
 * merespons, bukan statis di tengah selalu.
 *
 * Konten tiap halaman TETAP View hidup sepenuhnya -- link, tombol Bagikan,
 * foto/video semua tetap bisa disentuh walau halaman sedang berputar.
 *
 * Cara pakai:
 *   val transformer = BookFlipPageTransformer()
 *   proyektorBuku.setPageTransformer(transformer)
 *   // lalu update transformer.rasioSentuhanY dari touch listener terpisah
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    /**
     * Posisi vertikal sentuhan terakhir, 0f (paling atas) .. 1f (paling
     * bawah). Di-update dari luar kelas ini lewat MainActivity. Default
     * 0.5f (tengah) dipakai selama belum ada sentuhan tercatat sama sekali.
     */
    var rasioSentuhanY: Float = 0.5f

    private val elevasiMaks = 28f
    private val skalaMinimum = 0.94f
    private val zHalamanBerputar = 8f
    private val zHalamanBerikutnya = 0f

    override fun transformPage(page: View, position: Float) {
        val lebarHalaman = page.width
        if (lebarHalaman == 0) return

        page.cameraDistance = 24000f * page.resources.displayMetrics.density
        val pivotYSesuaiSentuhan = page.height * rasioSentuhanY

        when {
            position < -1f || position > 1f -> {
                page.alpha = 0f
                page.elevation = 0f
            }

            position <= 0f -> {
                val progresRotasi = abs(position)
                val intensitasVisual = sin(PI.toFloat() * progresRotasi)

                page.alpha = 1f
                page.translationX = 0f
                page.translationZ = zHalamanBerputar
                page.pivotX = lebarHalaman.toFloat()
                page.pivotY = pivotYSesuaiSentuhan
                // TUNE: kalau arah putaran kebalik di HP, ganti jadi: -90f * position
                page.rotationY = 90f * position

                val skala = 1f - (1f - skalaMinimum) * intensitasVisual
                page.scaleX = skala
                page.scaleY = skala

                page.elevation = elevasiMaks * intensitasVisual

                terapkanBayanganLengkung(page, intensitasVisual)
            }

            else -> {
                page.alpha = 1f
                page.translationX = -lebarHalaman * position
                page.translationZ = zHalamanBerikutnya
                page.elevation = 0f
                page.rotationY = 0f
                page.pivotX = 0f
                page.pivotY = pivotYSesuaiSentuhan
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
