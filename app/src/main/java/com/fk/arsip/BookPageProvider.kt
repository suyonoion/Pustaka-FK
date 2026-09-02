package com.fk.arsip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.bumptech.glide.Glide
import com.fk.arsip.curl.CurlPage
import com.fk.arsip.curl.BudayakanBaca
import com.fk.arsip.database.ArsipEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Merender tiap "halaman" (sampul depan, arsip, sampul belakang) menjadi
 * Bitmap untuk dipakai sebagai tekstur di BudayakanBaca (CurlView OpenGL).
 *
 * CATATAN ARSITEKTUR PENTING -- kenapa desain thread-nya seperti ini:
 * updatePage() dipanggil dari GL THREAD milik GLSurfaceView (bukan UI
 * thread), karena dipicu dari updatePages()/onDrawFrame() di BudayakanBaca.
 * Tapi operasi inflate+measure+layout+draw View HARUS di UI thread (aturan
 * dasar Android View system) -- sedangkan fetch gambar lewat Glide TIDAK
 * BOLEH dilakukan di UI thread kalau itu memblokir menunggu jaringan
 * (resiko ANR). Solusinya dipisah 2 tahap:
 *   1. Fetch bitmap foto (kalau ada) secara BLOCKING lewat Glide di thread
 *      pemanggil saat ini (GL thread) -- ini memang cara resmi Glide
 *      dipakai di background thread, aman.
 *   2. Baru inflate+bind+draw View ke Canvas di-dispatch ke UI thread lewat
 *      Handler, dan GL thread MENUNGGU (CountDownLatch) sampai selesai --
 *      supaya urutan render tetap benar (GL thread butuh Bitmap jadi
 *      sebelum lanjut), tanpa pernah menyentuh View dari luar UI thread.
 *
 * PENYEDERHANAAN yang disengaja dibanding BukuAdapter (RecyclerView) yang
 * asli: hanya menampilkan SATU foto representatif per halaman (bukan grid
 * multi-foto penuh), dan video ditampilkan sebagai gambar diam + ikon play
 * (tidak bisa diputar langsung di sini -- ini keterbatasan mendasar OpenGL
 * texture, bukan bug). Untuk melihat SEMUA foto/video & interaksi penuh,
 * pakai tombol "Lampiran" di bar aksi (lihat MainActivity) yang membuka
 * dialog terpisah pakai kode yang sama persis dengan grid biasa.
 */
class BookPageProvider(
    private val context: Context,
    private val ambilData: () -> List<ArsipEntity>
) : BudayakanBaca.PageProvider {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val warnaKertas = 0xFFFFFDF7.toInt()
    private val warnaSampulBack = Color.rgb(160, 155, 140)

    override fun getPageCount(): Int = ambilData().size + 2 // +sampul depan +sampul belakang

    /** Dipanggil dari GL thread. Index arsip asli untuk index halaman ini, atau null kalau sampul. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val total = ambilData().size
        return if (indexHalaman in 1..total) indexHalaman - 1 else null
    }

    override fun updatePage(page: CurlPage, width: Int, height: Int, index: Int) {
        val total = ambilData().size
        val bitmap: Bitmap = when {
            index == 0 -> renderSampul(width, height, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan")
            index == total + 1 -> renderSampul(width, height, judul = "Tamat", subjudul = "Pustaka FK")
            else -> {
                val arsip = ambilData().getOrNull(index - 1)
                if (arsip != null) {
                    renderHalamanArsip(width, height, arsip, index, total)
                } else {
                    // Data belum/tidak tersedia (mis. list berubah di tengah jalan) --
                    // tampilkan halaman kosong alih-alih crash.
                    renderKosong(width, height)
                }
            }
        }
        page.setTexture(bitmap, CurlPage.SIDE_FRONT)
        page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
    }

    // ------------------------------------------------------------------
    // SAMPUL
    // ------------------------------------------------------------------
    private fun renderSampul(width: Int, height: Int, judul: String, subjudul: String): Bitmap {
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_sampul_depan, null, true)
            view.findViewById<TextView>(R.id.txtJudulSampul)?.text = judul
            view.findViewById<TextView>(R.id.txtSubjudulSampul)?.text = subjudul
            view
        }
    }

    private fun renderKosong(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(warnaKertas)
        return bmp
    }

    // ------------------------------------------------------------------
    // HALAMAN ARSIP
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(width: Int, height: Int, arsip: ArsipEntity, indexHalaman: Int, totalArsip: Int): Bitmap {
        // TAHAP 1 (thread saat ini / GL thread): fetch foto representatif
        // SECARA BLOCKING lewat Glide, SEBELUM menyentuh UI thread sama
        // sekali -- supaya UI thread tidak pernah menunggu jaringan.
        var fotoRepresentatif: Bitmap? = null
        var isVideo = false
        var jumlahMediaLain = 0
        if (arsip.daftarFoto.isNotBlank()) {
            val daftar = arsip.daftarFoto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (daftar.isNotEmpty()) {
                val pertama = daftar[0]
                isVideo = pertama.startsWith("video:")
                val urlBersih = pertama.removePrefix("video:").removePrefix("image:")
                jumlahMediaLain = daftar.size - 1
                fotoRepresentatif = try {
                    Glide.with(context).asBitmap().load(urlBersih)
                        .submit(width, (height * 0.35f).toInt().coerceAtLeast(1))
                        .get(6, TimeUnit.SECONDS) // batas waktu supaya GL thread tidak menggantung selamanya kalau jaringan macet
                } catch (e: Exception) {
                    null
                }
            }
        }

        // TAHAP 2: inflate + bind + gambar ke Canvas, WAJIB di UI thread.
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_buku, null, true)
            view.background = KertasBergarisDrawable(density = context.resources.displayMetrics.density)

            val txtKontenUtama = view.findViewById<TextView>(R.id.txtKontenUtama)
            val txtKontenShared = view.findViewById<TextView>(R.id.txtKontenShared)
            val tinggiBarisPx = (KertasBergarisDrawable.TINGGI_BARIS_DP * context.resources.displayMetrics.density).toInt()
            TextViewCompat.setLineHeight(txtKontenUtama, tinggiBarisPx)
            TextViewCompat.setLineHeight(txtKontenShared, tinggiBarisPx)

            val kontenBersih = arsip.kontenPenuh
            val wadahDinamisKonten = view.findViewById<android.widget.LinearLayout>(R.id.wadahDinamisKonten)
            val wadahHeaderShared = view.findViewById<android.widget.LinearLayout>(R.id.wadahHeaderShared)
            val txtNamaPemilikShared = view.findViewById<TextView>(R.id.txtNamaPemilikShared)

            if (kontenBersih.contains("--- Membagikan Status:")) {
                val bagian = kontenBersih.split("\n\n--- Membagikan Status: ")
                val teksAsli = bagian[0].trim()
                if (teksAsli.isNotEmpty()) {
                    txtKontenUtama.text = teksAsli
                    txtKontenUtama.visibility = View.VISIBLE
                } else {
                    txtKontenUtama.visibility = View.GONE
                }
                val bantalanPx = (12 * context.resources.displayMetrics.density).toInt()
                wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
                wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)
                if (bagian.size > 1) {
                    val detail = bagian[1].split(" ---\n", limit = 2)
                    wadahHeaderShared.visibility = View.VISIBLE
                    txtKontenShared.visibility = View.VISIBLE
                    txtNamaPemilikShared.text = detail[0].trim()
                    if (detail.size > 1) {
                        txtKontenShared.text = detail[1].trim()
                    } else {
                        txtKontenShared.visibility = View.GONE
                    }
                }
            } else {
                txtKontenUtama.text = warnaiKontenTanyaJawab(kontenBersih)
                txtKontenUtama.visibility = View.VISIBLE
                wadahDinamisKonten.setBackgroundResource(0)
                wadahDinamisKonten.setPadding(0, 0, 0, 0)
                wadahHeaderShared.visibility = View.GONE
                txtKontenShared.visibility = View.GONE
            }

            view.findViewById<TextView>(R.id.txtTanggal).text = arsip.tanggalBaca
            view.findViewById<TextView>(R.id.txtKategori).text = arsip.kategori
            view.findViewById<TextView>(R.id.txtNomorHalaman).text = "Halaman : $indexHalaman/${totalArsip + 2}"
            view.findViewById<ImageView>(R.id.imgProfilAbah)?.setImageResource(R.drawable.profil_abah)

            // Foto/video representatif (kalau ada) -- sudah di-fetch di Tahap 1,
            // tinggal ditempel, TIDAK ada pemanggilan Glide async di sini.
            val wadahFoto = view.findViewById<android.widget.LinearLayout>(R.id.wadahMultiFoto)
            wadahFoto.removeAllViews()
            if (fotoRepresentatif != null) {
                wadahFoto.visibility = View.VISIBLE
                val bingkai = FrameLayout(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 500
                    ).apply { setMargins(0, 12, 0, 12) }
                    setBackgroundResource(R.drawable.bg_border_media)
                    setPadding(6, 6, 6, 6)
                }
                val img = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(fotoRepresentatif)
                }
                bingkai.addView(img)
                if (isVideo) {
                    val playIcon = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(100, 100).apply { gravity = android.view.Gravity.CENTER }
                        setImageResource(android.R.drawable.ic_media_play)
                        setColorFilter(Color.WHITE)
                    }
                    bingkai.addView(playIcon)
                }
                if (jumlahMediaLain > 0) {
                    val labelLebih = TextView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                            setMargins(0, 0, 12, 12)
                        }
                        text = "+$jumlahMediaLain lagi \u2192 tombol Lampiran"
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        setBackgroundColor(0x80000000.toInt())
                        setPadding(10, 5, 10, 5)
                    }
                    bingkai.addView(labelLebih)
                }
                wadahFoto.addView(bingkai)
            } else {
                wadahFoto.visibility = View.GONE
            }

            view
        }
    }

    private fun warnaiKontenTanyaJawab(teksLengkap: String): Spannable {
        val spannable = SpannableString(teksLengkap)
        val batas = teksLengkap.indexOf("=====")
        if (batas != -1) {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#004D40")), 0, batas, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#212121")), batas, teksLengkap.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#212121")), 0, teksLengkap.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    // ------------------------------------------------------------------
    // JEMBATAN GL THREAD <-> UI THREAD
    // ------------------------------------------------------------------
    /**
     * Menjalankan [buatView] di UI thread (wajib untuk operasi View), lalu
     * mengukur/menata/menggambarnya ke Bitmap, dan MENUNGGU (blocking thread
     * pemanggil -- GL thread) sampai selesai lewat CountDownLatch. Ada batas
     * waktu 4 detik sebagai jaring pengaman supaya GL thread tidak
     * menggantung selamanya kalau ada yang tidak beres di UI thread.
     */
    private fun renderViewKeBitmapDiMainThread(width: Int, height: Int, buatView: () -> View): Bitmap {
        val latch = CountDownLatch(1)
        var hasil: Bitmap? = null
        mainHandler.post {
            try {
                val view = buatView()
                val w = width.coerceAtLeast(1)
                val h = height.coerceAtLeast(1)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bmp))
                hasil = bmp
            } catch (e: Exception) {
                hasil = null
            } finally {
                latch.countDown()
            }
        }
        val selesai = latch.await(4, TimeUnit.SECONDS)
        return if (selesai && hasil != null) hasil!! else renderKosong(width, height)
    }
}
