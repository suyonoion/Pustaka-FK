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
import android.text.StaticLayout
import android.text.TextPaint
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
 * Merender tiap "halaman" menjadi Bitmap tekstur untuk BudayakanBaca (CurlView
 * OpenGL).
 *
 * ------------------------------------------------------------------
 * PENYEDERHANAAN BESAR (menggantikan sistem paginasi lama):
 * Sebelumnya 1 arsip BISA menempati lebih dari 1 halaman kalau kontennya
 * panjang -- itu perlu larik akumulatif GLOBAL (`kumulatif[]`) yang memetakan
 * "halaman ke berapa" ke "arsip yang mana", dihitung dari PERKIRAAN jumlah
 * halaman per arsip (StaticLayout baru dipakai persis begitu arsip itu benar-
 * benar dibuka). Estimasi yang meleset di SATU arsip menggeser pemetaan
 * SEMUA arsip sesudahnya -- itu akar dari seluruh rentetan bug lompat-
 * meleset, ArrayIndexOutOfBounds, dan race kondisi lintas-thread yang
 * berulang kali muncul.
 *
 * Sekarang: **1 ARSIP = 1 HALAMAN, SELALU.** Nomor arsip (posisi di
 * `ambilData()`) = nomor halaman, langsung, tanpa hitungan apa pun.
 * Tidak ada lagi larik akumulatif, tidak ada lagi kunci/lock, tidak ada lagi
 * estimasi yang bisa meleset. Konten yang panjang untuk sementara ditampilkan
 * dengan ukuran font yang menyesuaikan (mengecil supaya tetap muat 1
 * halaman); dipotong dengan catatan ke Sumber Asli kalau tetap tidak muat
 * walau sudah di ukuran font minimum. Scroll-di-dalam-halaman direncanakan
 * sebagai tahap berikutnya, terpisah dari perubahan ini.
 *
 * ------------------------------------------------------------------
 * THREADING (tidak berubah dari perbaikan sebelumnya):
 * updatePage() dipanggil dari GL THREAD dan harus SELALU langsung return.
 * Kalau bitmap final belum ada di cache, pasang placeholder instan, lalu
 * kerjakan render sesungguhnya (fetch Glide + inflate/draw View) di thread
 * background terpisah. Begitu selesai, panggil refreshHalaman(index) yang
 * diteruskan ke BudayakanBaca.refreshPageTexture() supaya tekstur halaman
 * yang sedang tampil diperbarui tanpa mengganggu animasi curl.
 *
 * Bitmap dari cache SELALU disalin (bukan diberikan objek aslinya) sebelum
 * diserahkan ke CurlPage, karena CurlPage.setTexture()/reset() me-recycle()
 * bitmap lama begitu diganti -- kalau cache & CurlPage berbagi objek yang
 * sama, cache ikut rusak (lihat histori perbaikan crash "recycled bitmap").
 */
class BookPageProvider(
    private val context: Context,
    private val ambilData: () -> List<ArsipEntity>,
    private val refreshHalaman: (Int) -> Unit
) : BudayakanBaca.PageProvider {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val warnaKertas = 0xFFFFFDF7.toInt()
    private val warnaSampulBack = Color.rgb(160, 155, 140)
    private val densitas get() = context.resources.displayMetrics.density

    private val executor = Executors.newFixedThreadPool(2)
    @Volatile private var shutdown = false

    private val cacheMaks = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cacheBitmap = object : LruCache<String, Bitmap>(cacheMaks) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val sedangDiproses = ConcurrentHashMap.newKeySet<String>()

    /**
     * Nomor arsip (posisi di ambilData(), 0-based) = nomor halaman - 1
     * (index 0 dicadangkan utk sampul depan). PEMETAAN LANGSUNG, tidak ada
     * hitungan/estimasi/lock apa pun -- lihat dokumentasi kelas di atas.
     */
    fun indexHalamanUntukArsip(posisiArsip: Int): Int = posisiArsip + 1

    /** Kebalikan dari indexHalamanUntukArsip -- null kalau sampul depan/belakang. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val posisiArsip = indexHalaman - 1
        val n = ambilData().size
        return if (posisiArsip in 0 until n) posisiArsip else null
    }

    override fun getPageCount(): Int = ambilData().size + 2 // + sampul depan + belakang

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    // ------------------------------------------------------------------
    /** Hasil resolusi index halaman -> cacheKey + tugas render latar belakangnya (tanpa efek samping). */
    private data class ResolusiHalaman(val cacheKey: String, val tugas: () -> Bitmap)

    private fun resolusiHalaman(index: Int, w: Int, h: Int, data: List<ArsipEntity>): ResolusiHalaman? {
        return when {
            index == 0 -> ResolusiHalaman("sampul_depan:${w}x$h") {
                renderSampul(w, h, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan")
            }
            index == data.size + 1 -> ResolusiHalaman("sampul_belakang:${w}x$h") {
                renderSampul(w, h, judul = "Tamat", subjudul = "Pustaka FK")
            }
            else -> {
                val arsipIndex = index - 1
                val arsip = data.getOrNull(arsipIndex) ?: return null
                ResolusiHalaman("${arsip.idPosting}:${w}x$h") {
                    renderHalamanArsip(w, h, arsip, arsipIndex + 1, data.size)
                }
            }
        }
    }

    /** Menjadwalkan render latar belakang utk `cacheKey` kalau belum sedang diproses; hasil masuk cache & memicu refreshHalaman(index). */
    private fun mintaRenderLatarBelakang(cacheKey: String, tugas: () -> Bitmap, index: Int) {
        if (sedangDiproses.add(cacheKey)) {
            executor.execute {
                try {
                    if (!shutdown) {
                        val bmp = tugas()
                        cacheBitmap.put(cacheKey, bmp)
                        refreshHalaman(index)
                    }
                } finally {
                    sedangDiproses.remove(cacheKey)
                }
            }
        }
    }

    override fun updatePage(page: CurlPage, width: Int, height: Int, index: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val data = ambilData()

        val resolusi = resolusiHalaman(index, w, h, data)
        if (resolusi == null) {
            page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            return
        }

        val fromCache = cacheBitmap.get(resolusi.cacheKey)
        if (fromCache != null) {
            page.setTexture(salinUntukTampil(fromCache, w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            prefetchTetangga(index, data.size, w, h, data)
            return
        }

        page.setTexture(renderPlaceholder(w, h), CurlPage.SIDE_FRONT)
        page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
        mintaRenderLatarBelakang(resolusi.cacheKey, resolusi.tugas, index)
    }

    /**
     * Render halaman kiri/kanan sekitar `index` di background lebih awal
     * (tanpa menunggu diminta), supaya waktu SWIPE terasa instan setelah
     * pengguna pernah singgah sebentar -- bukan cuma waktu dibuka persis.
     * Hanya jalan kalau belum ada di cache & belum sedang diproses.
     *
     * PERBAIKAN PENTING: versi lama membuat `CurlPage()` sekali-pakai dan
     * memanggil updatePage() dengannya dari THREAD BACKGROUND -- itu berarti
     * CurlPage.setTexture() (yang me-recycle() bitmap lama) ikut tersentuh
     * DI LUAR GL thread, melanggar kontrak GLSurfaceView (CurlPage/CurlMesh
     * cuma boleh disentuh dari GL thread). Sekarang prefetch CUMA mengisi
     * cache lewat resolusiHalaman()+mintaRenderLatarBelakang() -- TIDAK
     * PERNAH membuat atau menyentuh objek CurlPage sama sekali.
     */
    private fun prefetchTetangga(index: Int, totalArsip: Int, w: Int, h: Int, data: List<ArsipEntity>) {
        for (tetangga in intArrayOf(index - 1, index + 1, index + 2)) {
            if (tetangga < 0 || tetangga > totalArsip + 1) continue
            val resolusi = resolusiHalaman(tetangga, w, h, data) ?: continue
            // Cek cache dulu SECARA SINKRON (murah) sebelum menjadwalkan apa
            // pun -- updatePage() ini dipanggil tiap frame utk halaman yg
            // sedang tampil, jadi kalau tidak dicek dulu, tetangga yg SUDAH
            // di-cache akan terus-menerus dijadwalkan ulang ke executor tiap
            // frame (kerja sia-sia, membanjiri thread pool tanpa manfaat).
            if (cacheBitmap.get(resolusi.cacheKey) != null || sedangDiproses.contains(resolusi.cacheKey)) continue
            mintaRenderLatarBelakang(resolusi.cacheKey, resolusi.tugas, tetangga)
        }
    }

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

    private fun salinUntukTampil(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: renderKosong(width, height)
        } catch (e: OutOfMemoryError) {
            renderKosong(width, height)
        }
    }

    private fun renderSampul(width: Int, height: Int, judul: String, subjudul: String): Bitmap {
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_sampul_depan, null, true)
            view.findViewById<TextView>(R.id.txtJudulSampul)?.text = judul
            view.findViewById<TextView>(R.id.txtSubjudulSampul)?.text = subjudul
            view
        }
    }

    private fun lebarKonten(w: Int) = (w - ((48 + 16) * densitas)).toInt().coerceAtLeast(1)

    private fun tinggiBadan(h: Int, adaMedia: Boolean): Int {
        val cadanganChrome = (160 * densitas).toInt()
        val cadanganMedia = if (adaMedia) CADANGAN_MEDIA_PX else 0
        return (h - cadanganChrome - cadanganMedia).coerceAtLeast((80 * densitas).toInt())
    }

    companion object {
        private const val CADANGAN_MEDIA_PX = 560
        private const val UKURAN_FONT_MAKS_SP = 14f
        private const val UKURAN_FONT_MIN_SP = 9f
    }

    data class KontenBerbagi(val teksAsli: String, val namaPemilik: String, val kontenShared: String)

    /** Dipakai bersama oleh render & pengecekan panjang -- SATU tempat parsing, hindari duplikasi/inkonsistensi. */
    fun parseKontenBerbagi(kontenBersih: String): KontenBerbagi? {
        if (!kontenBersih.contains("--- Membagikan Status:")) return null
        val bagian = kontenBersih.split("\n\n--- Membagikan Status: ")
        val teksAsli = bagian[0].trim()
        if (bagian.size <= 1) return KontenBerbagi(teksAsli, "", "")
        val detail = bagian[1].split(" ---\n", limit = 2)
        val nama = detail[0].trim()
        val shared = if (detail.size > 1) detail[1].trim() else ""
        return KontenBerbagi(teksAsli, nama, shared)
    }

    @Suppress("DEPRECATION")
    private fun buatStaticLayout(teks: String, paint: TextPaint, lebarPx: Int) =
        StaticLayout(teks, paint, lebarPx, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)

    /**
     * PENYEDERHANAAN: karena 1 arsip SELALU 1 halaman (tidak ada lagi
     * paginasi lintas-halaman), teks yang panjang ditampilkan dengan ukuran
     * font yang MENGECIL bertahap sampai muat di tinggi yang tersedia.
     * Kalau bahkan di ukuran minimum masih tidak muat, teks dipotong +
     * catatan ke Sumber Asli -- lebih sederhana & aman drpd sistem
     * paginasi lama, walau blm ideal utk konten sangat panjang (rencana
     * tahap berikutnya: scroll sungguhan di dalam halaman).
     */
    private fun hitungUkuranFontMuat(teksUntukUkur: String, lebarPx: Int, tinggiTersediaPx: Int): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var ukuran = UKURAN_FONT_MAKS_SP * densitas
        val minimum = UKURAN_FONT_MIN_SP * densitas
        while (ukuran > minimum) {
            paint.textSize = ukuran
            val layout = buatStaticLayout(teksUntukUkur, paint, lebarPx)
            if (layout.height <= tinggiTersediaPx) return ukuran
            ukuran -= 0.5f * densitas
        }
        return minimum
    }

    /** Potong `teks` supaya tingginya muat di `tinggiTersediaPx` pada `ukuranFontPx`, tambahkan catatan kalau terpotong. */
    private fun potongAgarMuat(teks: String, paint: TextPaint, lebarPx: Int, tinggiTersediaPx: Int): String {
        val layoutPenuh = buatStaticLayout(teks, paint, lebarPx)
        if (layoutPenuh.height <= tinggiTersediaPx) return teks
        val catatan = "\n\n\u2026 (dipotong, baca lengkap lewat tombol Sumber Asli)"
        val layoutCatatan = buatStaticLayout(catatan, paint, lebarPx)
        val budgetTeks = (tinggiTersediaPx - layoutCatatan.height).coerceAtLeast(0)
        var batasBaris = 0
        for (baris in 0 until layoutPenuh.lineCount) {
            if (layoutPenuh.getLineBottom(baris) > budgetTeks) break
            batasBaris = baris + 1
        }
        if (batasBaris <= 0) return catatan.trim()
        val batasKarakter = layoutPenuh.getLineEnd(batasBaris - 1)
        return teks.substring(0, batasKarakter.coerceIn(0, teks.length)) + catatan
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
    // HALAMAN ARSIP (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(width: Int, height: Int, arsip: ArsipEntity, nomorArsip: Int, totalArsip: Int): Bitmap {
        val adaMedia = arsip.daftarFoto.isNotBlank()
        val lebarKontenPx = lebarKonten(width)
        val tinggiBadanPx = tinggiBadan(height, adaMedia)

        var fotoRepresentatif: Bitmap? = null
        var isVideo = false
        var jumlahMediaLain = 0
        if (adaMedia) {
            val daftar = arsip.daftarFoto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (daftar.isNotEmpty()) {
                val pertama = daftar[0]
                isVideo = pertama.startsWith("video:")
                val urlBersih = pertama.removePrefix("video:").removePrefix("image:")
                jumlahMediaLain = daftar.size - 1
                fotoRepresentatif = try {
                    // PENTING: applicationContext (bukan context Activity) --
                    // lihat histori perbaikan crash "freePixels" terkait Glide
                    // di dokumentasi kelas.
                    Glide.with(context.applicationContext).asBitmap().load(urlBersih)
                        .submit(width, (height * 0.35f).toInt().coerceAtLeast(1))
                        .get(6, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
            }
        }

        val kontenBersih = arsip.kontenPenuh
        val kb = parseKontenBerbagi(kontenBersih)
        // Ukur SEKALI utk seluruh konten halaman ini (asli + shared kalau
        // ada) supaya kedua blok konsisten pakai ukuran font yang sama.
        val teksUntukUkur = if (kb != null) "${kb.teksAsli}\n${kb.kontenShared}" else kontenBersih
        val ukuranFontPx = hitungUkuranFontMuat(teksUntukUkur, lebarKontenPx, tinggiBadanPx)
        val paintUkur = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = ukuranFontPx }

        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_buku, null, true)
            view.background = KertasBergarisDrawable(density = context.resources.displayMetrics.density)

            val txtKontenUtama = view.findViewById<TextView>(R.id.txtKontenUtama)
            val txtKontenShared = view.findViewById<TextView>(R.id.txtKontenShared)
            txtKontenUtama.textSize = ukuranFontPx / densitas
            txtKontenShared.textSize = ukuranFontPx / densitas
            val tinggiBarisPx = (KertasBergarisDrawable.TINGGI_BARIS_DP * context.resources.displayMetrics.density).toInt()
            TextViewCompat.setLineHeight(txtKontenUtama, tinggiBarisPx)
            TextViewCompat.setLineHeight(txtKontenShared, tinggiBarisPx)

            val wadahDinamisKonten = view.findViewById<android.widget.LinearLayout>(R.id.wadahDinamisKonten)
            val wadahHeaderShared = view.findViewById<android.widget.LinearLayout>(R.id.wadahHeaderShared)
            val txtNamaPemilikShared = view.findViewById<TextView>(R.id.txtNamaPemilikShared)

            if (kb == null) {
                val potongan = potongAgarMuat(kontenBersih, paintUkur, lebarKontenPx, tinggiBadanPx)
                txtKontenUtama.text = warnaiKontenTanyaJawab(potongan)
                txtKontenUtama.visibility = View.VISIBLE
                wadahDinamisKonten.setBackgroundResource(0)
                wadahDinamisKonten.setPadding(0, 0, 0, 0)
                wadahHeaderShared.visibility = View.GONE
                txtKontenShared.visibility = View.GONE
            } else {
                // "Shared status": bagi budget tinggi kasar 45/45 antara teks
                // asli & teks shared (sederhana -- tidak perlu presisi krn
                // masing masing sudah dipotong kalau perlu di potongAgarMuat()).
                val tinggiUntukAsli = (tinggiBadanPx * 0.45f).toInt()
                val tinggiUntukShared = (tinggiBadanPx * 0.45f).toInt()
                if (kb.teksAsli.isNotBlank()) {
                    val potongan = potongAgarMuat(kb.teksAsli, paintUkur, lebarKontenPx, tinggiUntukAsli)
                    txtKontenUtama.text = warnaiKontenTanyaJawab(potongan)
                    txtKontenUtama.visibility = View.VISIBLE
                } else {
                    txtKontenUtama.visibility = View.GONE
                }
                val bantalanPx = (12 * context.resources.displayMetrics.density).toInt()
                wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
                wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)
                wadahHeaderShared.visibility = if (kb.namaPemilik.isNotBlank()) View.VISIBLE else View.GONE
                if (kb.namaPemilik.isNotBlank()) txtNamaPemilikShared.text = kb.namaPemilik
                if (kb.kontenShared.isNotBlank()) {
                    val lebarSharedPx = (lebarKontenPx - (24 * context.resources.displayMetrics.density)).toInt().coerceAtLeast(1)
                    val potongan = potongAgarMuat(kb.kontenShared, paintUkur, lebarSharedPx, tinggiUntukShared)
                    txtKontenShared.text = potongan
                    txtKontenShared.visibility = View.VISIBLE
                } else {
                    txtKontenShared.visibility = View.GONE
                }
            }

            view.findViewById<TextView>(R.id.txtTanggal).text = arsip.tanggalBaca
            view.findViewById<TextView>(R.id.txtKategori).text = arsip.kategori
            view.findViewById<TextView>(R.id.txtNomorHalaman).text = "Halaman : $nomorArsip/$totalArsip"
            view.findViewById<ImageView>(R.id.imgProfilAbah)?.setImageResource(R.drawable.profil_abah)
            view.findViewById<View>(R.id.wadahProfilPenulis).visibility = View.VISIBLE
            view.findViewById<View>(R.id.wadahFooterDekoratif).visibility = View.GONE

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

    // ------------------------------------------------------------------
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
