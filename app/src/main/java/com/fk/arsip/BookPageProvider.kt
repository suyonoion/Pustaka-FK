package com.fk.arsip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.LruCache
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Merender tiap "halaman" (sampul depan, arsip, sampul belakang) menjadi
 * Bitmap untuk dipakai sebagai tekstur di BudayakanBaca (CurlView OpenGL).
 *
 * CATATAN ARSITEKTUR PENTING -- kenapa desain thread-nya seperti ini:
 * updatePage() dipanggil dari GL THREAD milik GLSurfaceView (bukan UI
 * thread), lewat updatePages()/onDrawFrame() di BudayakanBaca. GL thread
 * TIDAK BOLEH diblokir lama -- kalau diblokir, seluruh CurlView "membeku"
 * (halaman kosong / terasa berat), karena GL thread juga yang menggambar
 * frame & memproses animasi curl.
 *
 * PERBAIKAN (dulu vs sekarang):
 *   DULU: updatePage() blocking penuh di GL thread -- fetch Glide (sampai
 *   6 detik) + inflate/draw View di UI thread lewat CountDownLatch (sampai
 *   4 detik) -- SETIAP KALI halaman itu tampil, TANPA cache. Itu sebabnya
 *   konten lama muncul (halaman kosong dulu) dan navigasi terasa berat.
 *
 *   SEKARANG: updatePage() SELALU langsung return (non-blocking):
 *     - kalau bitmap final untuk index itu sudah ada di cache -> pakai itu.
 *     - kalau belum -> pasang placeholder "Memuat..." (digambar langsung,
 *       tanpa inflate View, jadi instan), lalu kirim tugas render
 *       sesungguhnya (fetch Glide + inflate/draw View) ke THREAD BACKGROUND
 *       terpisah (bukan GL thread, bukan UI thread -- UI thread cuma
 *       dipinjam sebentar utk inflate/measure/layout/draw via Handler).
 *   Begitu hasil akhirnya siap, disimpan ke cache lalu `refreshHalaman(index)`
 *   dipanggil -- ini meneruskan ke BudayakanBaca.refreshPageTexture(), yang
 *   sudah memang disiapkan untuk kasus ini: kalau halaman itu masih sedang
 *   tampil, tekstur-nya diperbarui & frame digambar ulang, tanpa mengganggu
 *   animasi curl yang sedang berjalan.
 *
 * Cache di-key pakai ID arsip (idPosting) + ukuran bitmap, BUKAN index
 * halaman -- supaya tetap valid walau daftar/filter berubah, dan otomatis
 * "batal berlaku" sendiri kalau ukuran layar berubah (rotasi).
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
    private val ambilData: () -> List<ArsipEntity>,
    private val refreshHalaman: (Int) -> Unit
) : BudayakanBaca.PageProvider {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val warnaKertas = 0xFFFFFDF7.toInt()
    private val warnaSampulBack = Color.rgb(160, 155, 140)

    // Thread pool kecil khusus utk kerja render halaman (fetch foto + gambar
    // View ke bitmap). Dibatasi 2 thread supaya tidak membanjiri Glide/UI
    // thread saat banyak halaman diminta beruntun (mis. swipe cepat).
    private val executor = Executors.newFixedThreadPool(2)
    @Volatile private var shutdown = false

    // Cache bitmap final, di-key per idPosting+ukuran (atau kunci khusus utk
    // sampul). Dibatasi berdasarkan total memori bitmap (bukan jumlah entri)
    // supaya tidak memicu OOM di buku dengan ribuan halaman.
    private val cacheMaks = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cacheBitmap = object : LruCache<String, Bitmap>(cacheMaks) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Index halaman yang sedang diproses di background, supaya tidak
    // dobel-render kalau GL thread minta halaman yang sama berkali-kali
    // sebelum hasil pertama selesai (lumrah terjadi selama animasi curl).
    private val sedangDiproses = ConcurrentHashMap.newKeySet<String>()

    override fun getPageCount(): Int = ambilData().size + 2 // +sampul depan +sampul belakang

    /** Dipanggil dari GL thread. Index arsip asli untuk index halaman ini, atau null kalau sampul. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val total = ambilData().size
        return if (indexHalaman in 1..total) indexHalaman - 1 else null
    }

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    override fun updatePage(page: CurlPage, width: Int, height: Int, index: Int) {
        val total = ambilData().size
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)

        val cacheKey: String
        val arsip: ArsipEntity?
        when {
            index == 0 -> {
                cacheKey = "sampul_depan:${w}x$h"
                arsip = null
            }
            index == total + 1 -> {
                cacheKey = "sampul_belakang:${w}x$h"
                arsip = null
            }
            else -> {
                val a = ambilData().getOrNull(index - 1)
                if (a == null) {
                    // Data belum/tidak tersedia (mis. list berubah di tengah jalan) --
                    // tampilkan halaman kosong alih-alih crash. Tidak perlu cache/async.
                    page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
                    page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
                    return
                }
                arsip = a
                cacheKey = "${a.idPosting}:${w}x$h"
            }
        }

        val fromCache = cacheBitmap.get(cacheKey)
        if (fromCache != null) {
            page.setTexture(fromCache, CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            return
        }

        // Belum ada di cache -> tampilkan placeholder INSTAN (gambar langsung
        // ke Canvas, tanpa inflate View, tanpa menyentuh UI thread), lalu
        // GL thread lanjut tanpa menunggu.
        page.setTexture(renderPlaceholder(w, h), CurlPage.SIDE_FRONT)
        page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)

        if (sedangDiproses.add(cacheKey)) {
            executor.execute {
                try {
                    if (shutdown) return@execute
                    val bmp = when {
                        index == 0 -> renderSampul(w, h, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan")
                        index == total + 1 -> renderSampul(w, h, judul = "Tamat", subjudul = "Pustaka FK")
                        else -> renderHalamanArsip(w, h, arsip!!, index, total)
                    }
                    if (!shutdown) {
                        cacheBitmap.put(cacheKey, bmp)
                        refreshHalaman(index)
                    }
                } finally {
                    sedangDiproses.remove(cacheKey)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // PLACEHOLDER (dipanggil langsung dari GL thread -- harus instan)
    // ------------------------------------------------------------------
    private fun renderPlaceholder(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(warnaKertas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E8C")
            textSize = height * 0.035f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Memuat\u2026", width / 2f, height / 2f, paint)
        return bmp
    }

    private fun renderKosong(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(warnaKertas)
        return bmp
    }

    // ------------------------------------------------------------------
    // SAMPUL (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderSampul(width: Int, height: Int, judul: String, subjudul: String): Bitmap {
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_sampul_depan, null, true)
            view.findViewById<TextView>(R.id.txtJudulSampul)?.text = judul
            view.findViewById<TextView>(R.id.txtSubjudulSampul)?.text = subjudul
            view
        }
    }

    // ------------------------------------------------------------------
    // HALAMAN ARSIP (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(width: Int, height: Int, arsip: ArsipEntity, indexHalaman: Int, totalArsip: Int): Bitmap {
        // TAHAP 1 (thread background `executor`, BUKAN GL thread lagi): fetch
        // foto representatif secara blocking lewat Glide -- aman di sini
        // karena tidak memblokir GL thread maupun UI thread.
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
                        .get(6, TimeUnit.SECONDS) // batas waktu jaring pengaman kalau jaringan macet
                } catch (e: Exception) {
                    null
                }
            }
        }

        // TAHAP 2: inflate + bind + gambar ke Canvas, WAJIB di UI thread --
        // dipinjam sebentar lewat Handler, thread background ini menunggu.
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
    // JEMBATAN THREAD BACKGROUND <-> UI THREAD
    // ------------------------------------------------------------------
    /**
     * Menjalankan [buatView] di UI thread (wajib untuk operasi View), lalu
     * mengukur/menata/menggambarnya ke Bitmap, dan MENUNGGU (blocking thread
     * pemanggil -- salah satu thread di `executor`, BUKAN GL thread lagi)
     * sampai selesai lewat CountDownLatch. Ada batas waktu 4 detik sebagai
     * jaring pengaman supaya thread background tidak menggantung selamanya
     * kalau ada yang tidak beres di UI thread.
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
