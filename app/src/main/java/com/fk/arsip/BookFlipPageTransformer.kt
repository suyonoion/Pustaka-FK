package com.fk.arsip

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * PageTransformer efek "halaman buku dibalik" (page-flip 3D), versi rombak
 * total. Murni transformasi View bawaan Android -- TANPA OpenGL, TANPA
 * library eksternal, konten tiap halaman tetap View hidup (teks tetap bisa
 * di-scroll/select).
 *
 * PERUBAHAN DARI VERSI SEBELUMNYA (berdasar screenshot: bayangan rata gelap
 * menutupi teks, bentuk kaku trapesium, transisi kurang mulus):
 *  1. Bayangan lipatan sekarang HANYA terkonsentrasi ~30% dekat sisi engsel
 *     (pivot), bukan rata menggelapkan seluruh halaman -- teks di sisi jauh
 *     dari lipatan tetap terbaca jelas.
 *  2. Ditambah bayangan jatuh (elevation) SUNGGUHAN yang menguat saat
 *     halaman terangkat berputar -- ini yang paling banyak menyumbang kesan
 *     "kertas terangkat", bukan sekadar kartu datar diputar.
 *  3. Sedikit scale-down (mengecil tipis) saat berputar untuk kesan jarak/
 *     kedalaman, dan cameraDistance diperbesar supaya distorsi perspektif
 *     lebih halus (tidak terlalu "gepeng-terpelintir").
 *
 * CATATAN JUJUR: ini tetap bidang datar kaku yang diputar (rotationY),
 * bukan lengkungan silinder sungguhan seperti curl OpenGL -- tapi dengan
 * kombinasi shadow+elevation+scale di atas, kesan "kertas" jauh lebih
 * meyakinkan dibanding versi sebelumnya.
 *
 * Cara pakai:
 *   proyektorBuku.setPageTransformer(BookFlipPageTransformer())
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    private val alphaBayanganMaks = 130   // 0-255 -- puncak kegelapan HANYA di garis lipatan
    private val elevasiMaks = 28f          // px, kekuatan bayangan-jatuh saat halaman terangkat penuh
    private val skalaMinimum = 0.94f       // seberapa mengecil halaman saat berputar penuh
    private val zHalamanBerputar = 8f
    private val zHalamanBerikutnya = 0f

    override fun transformPage(page: View, position: Float) {
        val lebarHalaman = page.width
        if (lebarHalaman == 0) return

        // Kamera diperbesar dari versi sebelumnya supaya perspektifnya lebih
        // halus (tidak terlihat gepeng/terpelintir kasar seperti di screenshot).
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

                // Bayangan jatuh sungguhan (drop shadow), menguat seiring
                // halaman "terangkat" dari permukaan halaman di baliknya.
                page.elevation = elevasiMaks * intensitas

                terapkanBayanganLipatan(page, intensitas)
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

    /**
     * Bayangan lipatan yang HANYA gelap di ~30% terakhir dekat sisi engsel
     * (kanan, arah pivot), transparan penuh di sisa halaman -- supaya teks
     * di bagian yang jauh dari lipatan tidak ikut tertutup abu-abu.
     */
    private fun terapkanBayanganLipatan(page: View, intensitas: Float) {
        val overlayLama = page.tag as? GradientDrawable
        val overlay = overlayLama ?: GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                0x00000000, // 0%   -- transparan penuh
                0x00000000, // 33%  -- transparan penuh
                0x00000000, // 66%  -- transparan penuh
                0x00000000, // 90%  -- mulai gelap dikit (diisi ulang di bawah)
                0xFF000000.toInt() // 100% -- paling gelap tepat di garis lipatan
            )
        ).also {
            page.foreground = it
            page.tag = it
        }
        // Alpha maksimum di titik gelap terpekat mengikuti intensitas putaran.
        overlay.alpha = (intensitas * alphaBayanganMaks).toInt().coerceIn(0, alphaBayanganMaks)
    }

    private fun bersihkanBayangan(page: View) {
        val overlay = page.tag as? GradientDrawable ?: return
        overlay.alpha = 0
    }
}
