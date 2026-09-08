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
import android.text.SpannableStringBuilder
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
import kotlin.math.ceil
import kotlin.math.max

/**
 * Merender tiap "halaman" menjadi Bitmap tekstur untuk BudayakanBaca (CurlView
 * OpenGL). Sejak update ini, 1 ARSIP BISA MENEMPATI LEBIH DARI 1 HALAMAN kalau
 * kontennya panjang -- lihat bagian "PAGINASI" di bawah untuk kenapa & caranya.
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
 *
 * ------------------------------------------------------------------
 * PAGINASI -- kenapa & bagaimana:
 * Sebelumnya 1 arsip = 1 halaman selalu, dan konten ditaruh di dalam
 * ScrollView di dalam item_buku.xml. Itu masalahnya: begitu View itu
 * "difoto" jadi Bitmap statis, ScrollView cuma menggambar apa yang
 * kelihatan di layar -- teks yang ada di bawah area yang kelihatan itu
 * SIMPLY TIDAK IKUT TERGAMBAR (bukan disembunyikan, betul-betul hilang dari
 * bitmap), makin parah di landscape karena tinggi layar lebih pendek.
 *
 * Perbaikannya: teks panjang dipecah jadi beberapa halaman (ukuran font
 * tetap, seperti buku asli), lewat 2 mekanisme terpisah:
 *
 *  1) PERKIRAAN CEPAT (untuk `getPageCount()` & lompat-ke-halaman dari
 *     drawer, lihat MainActivity.indexHalamanUntukArsip): dihitung pakai
 *     rumus kasar (panjang teks / perkiraan lebar-tinggi baris), BUKAN
 *     StaticLayout, supaya tetap instan walau datanya puluhan ribu arsip.
 *     Sengaja dibuat SEDIKIT BERLEBIH (bukan pas-pasan) supaya arahnya
 *     aman -- kalaupun meleset, meleset ke arah "kelebihan slot halaman"
 *     (paling buruk ada halaman nyaris kosong), BUKAN "kekurangan slot"
 *     (yang berarti balik lagi ke bug teks terpotong).
 *
 *  2) PEMOTONGAN PERSIS (untuk render sesungguhnya, per halaman yang
 *     benar-benar dibuka): pakai StaticLayout mengukur baris demi baris
 *     dari TEKS ASLI pada lebar sebenarnya, dipotong per halaman begitu
 *     tingginya akan melebihi area yang tersedia. Ini yang menjamin TIDAK
 *     ADA baris yang terpotong di tengah pada halaman yang benar-benar
 *     dibuka pengguna.
 *
 * KETERBATASAN YANG DISENGAJA (supaya scope tetap terkendali): paginasi
 * hanya berlaku untuk konten Tanya-Jawab biasa (txtKontenUtama). Postingan
 * bertipe "Membagikan Status" (ada blok status yang dibagikan ulang)
 * TETAP 1 halaman seperti sebelumnya -- kasus ini jauh lebih jarang & lebih
 * rumit strukturnya (ada 2 blok teks + kotak bersarang), jadi belum
 * dipaginasi. Kalau ini ternyata sering kepotong juga, kabari saya lagi.
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

    // ------------------------------------------------------------------
    // PETA HALAMAN (estimasi cepat, lihat dokumentasi kelas di atas)
    // ------------------------------------------------------------------
    private var wKumulatif = -1
    private var hKumulatif = -1
    private var nKumulatif = -1
    private var kumulatif: IntArray = IntArray(0) // kumulatif[i] = total slot halaman utk arsip[0 until i]

    private fun pastikanKumulatif(w: Int, h: Int) {
        val data = ambilData()
        if (w == wKumulatif && h == hKumulatif && data.size == nKumulatif) return
        val lebarKontenPx = lebarKonten(w)
        val arr = IntArray(data.size + 1)
        for (i in data.indices) {
            val a = data[i]
            val tinggiBadanPx = tinggiBadan(h, adaMedia = a.daftarFoto.isNotBlank())
            arr[i + 1] = arr[i] + perkiraanJumlahHalaman(a.kontenPenuh, lebarKontenPx, tinggiBadanPx)
        }
        kumulatif = arr
        wKumulatif = w; hKumulatif = h; nKumulatif = data.size
    }

    private fun lebarKonten(w: Int) = (w - ((48 + 16) * densitas)).toInt().coerceAtLeast(1)

    private fun tinggiBadan(h: Int, adaMedia: Boolean): Int {
        // Diturunkan dari 190dp -- blok "Sumber Asli/Bagikan" dekoratif (~44dp)
        // sekarang dihilangkan total dari setiap halaman (lihat renderHalamanArsip),
        // jadi sisa chrome cuma header+garis+padding (~146dp), dibulatkan ke
        // atas dgn sedikit margin aman.
        val cadanganChrome = (160 * densitas).toInt()
        val cadanganMedia = if (adaMedia) CADANGAN_MEDIA_PX else 0
        return (h - cadanganChrome - cadanganMedia).coerceAtLeast((80 * densitas).toInt())
    }

    companion object {
        // ~tinggi blok foto (lihat wadahMultiFoto), sengaja konservatif.
        // SATU tempat -- dipakai baik di tinggiBadan() (utk hitung cepat
        // per-arsip) maupun ambilRencanaTeks() (utk redistribusi 2-tahap),
        // supaya keduanya konsisten dan tidak drift satu sama lain.
        private const val CADANGAN_MEDIA_PX = 560
    }

    private fun perkiraanJumlahHalaman(teks: String, lebarKontenPx: Int, tinggiBadanPx: Int): Int {
        if (teks.isBlank()) return 1
        val kb = parseKontenBerbagi(teks)
        // Utk "shared status", jumlah karakter yg diperhitungkan = teks asli +
        // teks yg dibagikan ulang (keduanya sekarang ikut dipaginasi, lihat
        // ambilRencanaTeks) + sedikit ekstra utk header kotak "Status
        // Dibagikan" (~2 baris) supaya perkiraan tetap condong ke arah aman.
        val teksUntukDihitung = if (kb != null) "${kb.teksAsli}\n${kb.kontenShared}" else teks
        val totalKarakter = teksUntukDihitung.length + if (kb != null) 80 else 0
        val ukuranFontPx = 14f * densitas
        // PERBAIKAN: dulu cuma menghitung dari total karakter / karakter-per-
        // baris -- ini UNDER-ESTIMATE parah utk konten dgn banyak baris
        // PENDEK & banyak baris KOSONG antar-paragraf (gaya penulisan umum
        // di arsip ini: poin-poin pendek dipisah baris kosong). Baris kosong
        // ikut makan 1 baris penuh tapi menyumbang 0 karakter ke hitungan
        // panjang -- jadi perkiraan lama bisa jauh lebih kecil dari
        // kebutuhan asli, menyebabkan slot halaman kehabisan sebelum teks
        // sungguhan habis (teks "hilang" di tengah, padahal ada tanda
        // "Selanjutnya >>" yg menjanjikan lanjutannya). Sekarang jumlah
        // baris = MAKS(dari perkiraan lebar/panjang, dari jumlah baris
        // eksplisit "\n" -- baris eksplisit menjamin batas bawah yg tidak
        // mungkin di-under-estimate).
        val jumlahBarisEksplisit = teksUntukDihitung.count { it == '\n' } + 1
        val karakterPerBaris = max(1f, lebarKontenPx / (ukuranFontPx * 0.62f)) // 0.55->0.62: char dianggap lebih lebar, lebih konservatif
        val tinggiBarisPx = KertasBergarisDrawable.TINGGI_BARIS_DP * densitas
        val barisPerHalaman = max(1f, tinggiBadanPx / tinggiBarisPx)
        val jumlahBarisDariPanjang = ceil(totalKarakter / karakterPerBaris)
        val jumlahBaris = max(jumlahBarisEksplisit.toFloat(), jumlahBarisDariPanjang)
        // +1 halaman ekstra sbg jaring pengaman terakhir -- lebih baik ada
        // 1 halaman nyaris kosong di ujung drpd teks kehabisan slot lagi.
        return (ceil(jumlahBaris / barisPerHalaman).toInt() + 1).coerceAtLeast(1)
    }

    /** Dipakai MainActivity untuk lompat langsung ke arsip tertentu (mis. dari drawer). */
    /**
     * true kalau BookPageProvider sudah pernah tahu ukuran halaman
     * sungguhan (dari updatePage() yang sudah pernah dipanggil GL thread).
     * Dipakai MainActivity SEBELUM memanggil indexHalamanUntukArsip() --
     * lihat catatan panjang di fungsi itu soal kenapa ini penting.
     */
    fun ukuranSudahDiketahui(): Boolean = wKumulatif > 0 && hKumulatif > 0

    /**
     * Dipakai MainActivity utk lompat langsung ke arsip tertentu (mis. dari
     * drawer/grid). PENTING: hasilnya cuma benar kalau ukuran halaman
     * SUNGGUHAN sudah diketahui (lihat ukuranSudahDiketahui()) -- kalau
     * dipanggil SEBELUM itu (mis. sesaat setelah wadahModeBuku baru saja
     * diset VISIBLE, sebelum CurlView sempat di-layout & merender apa pun),
     * pastikanKumulatif() di bawah ini terpaksa jalan dgn ukuran 1x1 asal-
     * asalan, menghasilkan perkiraan jumlah halaman per arsip yang jauh
     * meleset (bisa berkali-kali lipat) -- itu sebabnya lompat ke arsip
     * no.5 pernah malah mendarat di halaman ~387. MainActivity WAJIB
     * menunggu ukuranSudahDiketahui()==true dulu sebelum memanggil ini.
     */
    fun indexHalamanUntukArsip(posisiArsip: Int): Int {
        pastikanKumulatif(wKumulatif.coerceAtLeast(1), hKumulatif.coerceAtLeast(1))
        val data = ambilData()
        if (posisiArsip !in data.indices) return 0
        return kumulatif[posisiArsip] + 1 // +1 krn index 0 = sampul depan
    }

    /** Index arsip asli (abaikan sub-halaman) untuk index halaman ini, atau null kalau sampul. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val posisiKonten = indexHalaman - 1
        if (posisiKonten < 0 || kumulatif.isEmpty() || posisiKonten >= kumulatif.last()) return null
        return cariArsipIndex(posisiKonten)
    }

    private fun cariArsipIndex(posisiKonten: Int): Int {
        var lo = 0
        var hi = kumulatif.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (kumulatif[mid] <= posisiKonten) lo = mid else hi = mid - 1
        }
        return lo
    }

    override fun getPageCount(): Int {
        val w = if (wKumulatif > 0) wKumulatif else 1
        val h = if (hKumulatif > 0) hKumulatif else 1
        pastikanKumulatif(w, h)
        return (if (kumulatif.isEmpty()) 0 else kumulatif.last()) + 2 // + sampul depan + belakang
    }

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    // ------------------------------------------------------------------
    /** Hasil resolusi index halaman -> cacheKey + tugas render latar belakangnya (tanpa efek samping). */
    private data class ResolusiHalaman(val cacheKey: String, val tugas: () -> Bitmap)

    private fun resolusiHalaman(index: Int, w: Int, h: Int): ResolusiHalaman? {
        val totalHalamanKonten = if (kumulatif.isEmpty()) 0 else kumulatif.last()
        return when {
            index == 0 -> ResolusiHalaman("sampul_depan:${w}x$h") {
                renderSampul(w, h, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan")
            }
            index == totalHalamanKonten + 1 -> ResolusiHalaman("sampul_belakang:${w}x$h") {
                renderSampul(w, h, judul = "Tamat", subjudul = "Pustaka FK")
            }
            else -> {
                val posisiKonten = index - 1
                if (posisiKonten < 0 || posisiKonten >= totalHalamanKonten) return null
                val arsipIndex = cariArsipIndex(posisiKonten)
                val arsip = ambilData().getOrNull(arsipIndex) ?: return null
                val subIndex = posisiKonten - kumulatif[arsipIndex]
                val perkiraanTotalSub = kumulatif[arsipIndex + 1] - kumulatif[arsipIndex]
                ResolusiHalaman("${arsip.idPosting}:${w}x$h:sub$subIndex") {
                    renderHalamanArsip(w, h, arsip, arsipIndex + 1, ambilData().size, subIndex, perkiraanTotalSub)
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
        pastikanKumulatif(w, h)
        val totalHalamanKonten = if (kumulatif.isEmpty()) 0 else kumulatif.last()

        val resolusi = resolusiHalaman(index, w, h)
        if (resolusi == null) {
            page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            return
        }

        val fromCache = cacheBitmap.get(resolusi.cacheKey)
        if (fromCache != null) {
            page.setTexture(salinUntukTampil(fromCache, w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            prefetchTetangga(index, totalHalamanKonten, w, h)
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
     * cuma boleh disentuh dari GL thread). Ini kemungkinan besar penyebab
     * crash native "freePixels" (segfault di GLThread) yang terjadi lagi
     * setelah fitur prefetch ditambahkan. Sekarang prefetch CUMA mengisi
     * cache lewat resolusiHalaman()+mintaRenderLatarBelakang() -- TIDAK
     * PERNAH membuat atau menyentuh objek CurlPage sama sekali.
     */
    private fun prefetchTetangga(index: Int, totalHalamanKonten: Int, w: Int, h: Int) {
        for (tetangga in intArrayOf(index - 1, index + 1, index + 2)) {
            if (tetangga < 0 || tetangga > totalHalamanKonten + 1) continue
            val resolusi = resolusiHalaman(tetangga, w, h) ?: continue
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

    // ------------------------------------------------------------------
    // PEMOTONGAN TEKS PERSIS (StaticLayout) -- dipanggil dari thread
    // background milik `executor`, aman melakukan kerja lumayan (bukan
    // GL thread / bukan UI thread).
    //
    // Sejak perbaikan ini, konten "shared status" (ada blok status yg
    // dibagikan ulang) IKUT dipaginasi juga -- sebelumnya cuma teks
    // Tanya-Jawab biasa yg dipaginasi, "shared status" masih 1 halaman
    // penuh (batasan yg didokumentasikan), dan itu yg menyebabkan bug
    // "masih terpotong" utk jenis konten ini. Sekarang keduanya dianggap
    // "unit-unit" yg dialirkan berurutan (baris teks asli, lalu header
    // kotak "Status Dibagikan", lalu baris teks yg dibagikan), dan
    // dikelompokkan per halaman berdasarkan tinggi kumulatifnya -- sama
    // seperti teks biasa, cuma sumbernya gabungan 2 blok teks + 1 header.
    // ------------------------------------------------------------------
    data class KontenBerbagi(val teksAsli: String, val namaPemilik: String, val kontenShared: String)

    /** Dipakai bersama oleh estimasi cepat & pemotongan persis -- SATU tempat parsing, hindari duplikasi/inkonsistensi. */
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

    /** Satu halaman bisa berisi potongan teks asli, header kotak shared, dan/atau potongan teks shared -- kombinasi mana pun, tergantung di mana batas halaman jatuh. */
    private data class UnitHalaman(val rentangAsli: IntRange?, val headerShared: Boolean, val rentangShared: IntRange?)
    private data class RencanaTeks(val potongan: List<UnitHalaman>)
    private val rencanaCache = ConcurrentHashMap<String, RencanaTeks>()

    @Suppress("DEPRECATION")
    private fun buatStaticLayout(teks: String, paint: TextPaint, lebarPx: Int) =
        StaticLayout(teks, paint, lebarPx, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)

    private fun ambilRencanaTeks(arsip: ArsipEntity, w: Int, h: Int): RencanaTeks {
        val key = "${arsip.idPosting}:${w}x$h"
        // PENTING: computeIfAbsent (bukan cek-lalu-simpan biasa) -- utk
        // status yang SANGAT panjang, prefetch tetangga + navigasi langsung
        // pengguna bisa sama-sama minta rencana arsip yang SAMA hampir
        // bersamaan dari 2 thread executor berbeda. Cek-lalu-simpan biasa
        // rawan race: dua-duanya sama-sama cache-miss lalu dua-duanya
        // menghitung ulang StaticLayout PENUH secara paralel -- pemborosan
        // yang bisa terasa sebagai "lama/tidak jelas responnya" pas
        // menyentuh status panjang. computeIfAbsent bersifat atomik per
        // key: thread kedua otomatis menunggu hasil thread pertama alih-
        // alih ikut menghitung ulang dari nol.
        return rencanaCache.computeIfAbsent(key) {
        val teksMentah = arsip.kontenPenuh.ifBlank { " " }
        val lebarKontenPx = lebarKonten(w)
        val adaMedia = arsip.daftarFoto.isNotBlank()
        // PENTING: pengelompokan tahap 1 SELALU pakai budget PENUH (anggap
        // tidak ada media), BUKAN tinggiBadan(h, adaMedia) -- kalau media
        // langsung dikurangkan di sini, SEMUA halaman arsip ini (termasuk
        // yg jauh dari halaman terakhir) kehilangan jatah tinggi utk ruang
        // foto yg sebenarnya cuma dipakai di 1 halaman -- itu penyebab bug
        // "banyak ruang kosong di halaman yg bukan halaman terakhir". Media
        // baru diperhitungkan BELAKANGAN, cuma utk kelompok paling akhir
        // (lihat redistribusi di bawah).
        val budgetPenuh = tinggiBadan(h, adaMedia = false)
        // PENTING: tinggi baris TETAP (samakan dgn TextViewCompat.setLineHeight
        // di render sungguhan), BUKAN tinggi alami font -- lihat catatan di
        // perkiraanJumlahHalaman() utk histori bug yg ini perbaiki.
        val tinggiBarisTetapPx = (KertasBergarisDrawable.TINGGI_BARIS_DP * densitas).toInt().coerceAtLeast(1)
        // Cadangkan 1 baris tambahan dari budget PENUH utk penanda
        // "Selanjutnya >>" -- baru benar-benar ditampilkan belakangan kalau
        // halaman ini TERNYATA bukan halaman terakhir arsipnya (lihat
        // renderHalamanArsip). Dicadangkan di SEMUA halaman spy selalu ada
        // ruang, bukan cuma dihitung setelah tahu halaman mana yg terakhir.
        val budgetUntukPembagian = (budgetPenuh - tinggiBarisTetapPx).coerceAtLeast(tinggiBarisTetapPx)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f * densitas }

        data class UnitMentah(val tinggi: Int, val asli: IntRange?, val header: Boolean, val shared: IntRange?)
        val unitMentah = mutableListOf<UnitMentah>()

        val kb = parseKontenBerbagi(teksMentah)
        if (kb == null) {
            val layout = buatStaticLayout(teksMentah, paint, lebarKontenPx)
            for (baris in 0 until layout.lineCount) {
                val akhir = if (baris + 1 < layout.lineCount) layout.getLineStart(baris + 1) else teksMentah.length
                unitMentah.add(UnitMentah(tinggiBarisTetapPx, layout.getLineStart(baris) until akhir, false, null))
            }
        } else {
            if (kb.teksAsli.isNotBlank()) {
                val layoutAsli = buatStaticLayout(kb.teksAsli, paint, lebarKontenPx)
                for (baris in 0 until layoutAsli.lineCount) {
                    val akhir = if (baris + 1 < layoutAsli.lineCount) layoutAsli.getLineStart(baris + 1) else kb.teksAsli.length
                    unitMentah.add(UnitMentah(tinggiBarisTetapPx, layoutAsli.getLineStart(baris) until akhir, false, null))
                }
            }
            if (kb.namaPemilik.isNotBlank() || kb.kontenShared.isNotBlank()) {
                // ~header nama+label & padding pembuka kotak "Status Dibagikan" --
                // perkiraan konservatif spy tidak under-estimate.
                unitMentah.add(UnitMentah((72 * densitas).toInt(), null, true, null))
            }
            if (kb.kontenShared.isNotBlank()) {
                val lebarSharedPx = (lebarKontenPx - (24 * densitas)).toInt().coerceAtLeast(1) // dikurangi padding kotak
                val layoutShared = buatStaticLayout(kb.kontenShared, paint, lebarSharedPx)
                for (baris in 0 until layoutShared.lineCount) {
                    val akhir = if (baris + 1 < layoutShared.lineCount) layoutShared.getLineStart(baris + 1) else kb.kontenShared.length
                    unitMentah.add(UnitMentah(tinggiBarisTetapPx, null, false, layoutShared.getLineStart(baris) until akhir))
                }
            }
        }
        if (unitMentah.isEmpty()) unitMentah.add(UnitMentah(0, 0 until 0, false, null))

        // TAHAP 1: kelompokkan pakai budget PENUH -- simpan sbg rentang INDEX
        // ke unitMentah (bukan langsung gabung ke char-range) supaya kelompok
        // TERAKHIR bisa dipecah lagi di tahap 2 kalau perlu.
        val kelompokIndex = mutableListOf<IntRange>()
        run {
            var idx = 0
            while (idx < unitMentah.size) {
                var tinggiTerpakai = 0
                var mulai = idx
                var sudahAdaSatu = false
                while (idx < unitMentah.size) {
                    val u = unitMentah[idx]
                    if (tinggiTerpakai + u.tinggi > budgetUntukPembagian && sudahAdaSatu) break
                    tinggiTerpakai += u.tinggi
                    sudahAdaSatu = true
                    idx++
                }
                kelompokIndex.add(mulai until idx)
            }
        }

        // TAHAP 2: kalau arsip ini ada media, media itu HANYA tampil di
        // kelompok/halaman PALING AKHIR (lihat renderHalamanArsip). Cek
        // apakah kelompok terakhir + cadangan media masih muat di budget
        // penuh -- kalau tidak, sisihkan unit-unit paling belakang dari
        // kelompok itu ke kelompok BARU (halaman baru), supaya kelompok
        // terakhir yg lama jadi cukup kecil utk berbagi tempat dgn foto.
        if (adaMedia && kelompokIndex.isNotEmpty()) {
            val budgetDenganMedia = tinggiBadan(h, adaMedia = true)
            val terakhir = kelompokIndex.last()
            var tinggiKelompokTerakhir = terakhir.sumOf { unitMentah[it].tinggi }
            if (tinggiKelompokTerakhir > budgetDenganMedia && terakhir.count() > 1) {
                var batasBaru = terakhir.last
                while (batasBaru > terakhir.first && tinggiKelompokTerakhir > budgetDenganMedia) {
                    tinggiKelompokTerakhir -= unitMentah[batasBaru].tinggi
                    batasBaru--
                }
                kelompokIndex[kelompokIndex.size - 1] = terakhir.first..batasBaru
                kelompokIndex.add((batasBaru + 1)..terakhir.last)
            }
        }

        val potongan = kelompokIndex.map { rentangIdx ->
            var asliMulai: Int? = null; var asliAkhir: Int? = null
            var header = false
            var sharedMulai: Int? = null; var sharedAkhir: Int? = null
            for (i in rentangIdx) {
                val u = unitMentah[i]
                u.asli?.let { if (asliMulai == null) asliMulai = it.first; asliAkhir = it.last + 1 }
                if (u.header) header = true
                u.shared?.let { if (sharedMulai == null) sharedMulai = it.first; sharedAkhir = it.last + 1 }
            }
            UnitHalaman(
                rentangAsli = if (asliMulai != null) asliMulai!! until asliAkhir!! else null,
                headerShared = header,
                rentangShared = if (sharedMulai != null) sharedMulai!! until sharedAkhir!! else null
            )
        }

        val hasil = RencanaTeks(potongan)
        hasil
        }
    }

    // ------------------------------------------------------------------
    // HALAMAN ARSIP (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(
        width: Int, height: Int, arsip: ArsipEntity, nomorArsip: Int, totalArsip: Int,
        subIndex: Int, perkiraanTotalSub: Int
    ): Bitmap {
        val rencana = ambilRencanaTeks(arsip, width, height)
        // Kalau perkiraan cepat "meleset kurang" (jarang, tapi bisa terjadi --
        // lihat dokumentasi kelas), subIndex bisa melebihi jumlah potongan
        // ASLI dari StaticLayout -- amankan dgn menampilkan potongan terakhir
        // yang tersedia, supaya tidak crash & tetap tidak ada teks yg hilang.
        val potonganIndex = subIndex.coerceAtMost(rencana.potongan.size - 1)
        val unit = rencana.potongan[potonganIndex]
        val halamanTerakhirDariArsipIni = potonganIndex == rencana.potongan.size - 1
        val lanjutan = potonganIndex > 0

        var fotoRepresentatif: Bitmap? = null
        var isVideo = false
        var jumlahMediaLain = 0
        // Foto/video representatif HANYA ditempel di halaman terakhir arsip
        // ini (lihat dokumentasi kelas: kenapa media selalu dicadangkan di
        // halaman terakhir, bukan menyebar di tengah teks).
        if (halamanTerakhirDariArsipIni && arsip.daftarFoto.isNotBlank()) {
            val daftar = arsip.daftarFoto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (daftar.isNotEmpty()) {
                val pertama = daftar[0]
                isVideo = pertama.startsWith("video:")
                val urlBersih = pertama.removePrefix("video:").removePrefix("image:")
                jumlahMediaLain = daftar.size - 1
                fotoRepresentatif = try {
                    // PENTING: Glide.with(context) -- context = Activity --
                    // mengikat request ke lifecycle Activity, jadi begitu
                    // Activity dihancurkan (app+recents ditutup), Glide
                    // OTOMATIS mendaur ulang bitmap yang masih terkait,
                    // padahal thread background ini (di luar lifecycle
                    // Activity secara sengaja) masih memegang/memakainya
                    // utk menggambar halaman. Itu race condition penyebab
                    // crash "freePixels" di thread utama saat app ditutup.
                    // applicationContext TIDAK terikat lifecycle Activity,
                    // jadi aman dipakai oleh pipeline background ini.
                    Glide.with(context.applicationContext).asBitmap().load(urlBersih)
                        .submit(width, (height * 0.35f).toInt().coerceAtLeast(1))
                        .get(6, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
            }
        }

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
            val kb = parseKontenBerbagi(kontenBersih)

            // Awalan "(lanjutan)" ditempel di teks mana pun yg PALING DULU
            // muncul di halaman ini (asli lebih dulu kalau ada, kalau tidak
            // ya di teks shared) -- supaya penanda cuma tampil sekali per
            // halaman, di posisi paling atas kontennya.
            var lanjutanSudahDipakai = !lanjutan
            // Penanda "Selanjutnya >>" ditempel di teks yg PALING BELAKANG
            // tampil di halaman ini -- HANYA kalau halaman ini BUKAN halaman
            // terakhir arsipnya (masih ada isi lagi di halaman berikutnya).
            // Ruang utk baris ini sudah dicadangkan sejak paginasi (lihat
            // budgetUntukPembagian di ambilRencanaTeks), jadi aman tidak
            // menyebabkan overflow/potongan baru.
            val penandaLanjut = if (!halamanTerakhirDariArsipIni) "\n\nSelanjutnya >>" else ""

            if (kb == null) {
                // Kasus normal (Tanya-Jawab, dsb): potong sesuai `unit.rentangAsli`
                // hasil StaticLayout, dgn pewarnaan yg tetap konsisten dgn teks
                // penuh. Rentang pakai konvensi `start until end` (end EKSKLUSIF),
                // jadi argumen akhir ke subSequence() harus `+1`.
                val rentang = unit.rentangAsli ?: (0 until 0)
                val awal = rentang.first.coerceIn(0, kontenBersih.length)
                val akhir = (rentang.last + 1).coerceIn(awal, kontenBersih.length)
                val potonganBerwarna = warnaiKontenTanyaJawab(kontenBersih)
                    .let { SpannableStringBuilder(it) }
                    .subSequence(awal, akhir)
                val builder = SpannableStringBuilder()
                if (!lanjutanSudahDipakai) {
                    lanjutanSudahDipakai = true
                    builder.append("\u21B3 (lanjutan halaman sebelumnya)\n\n")
                }
                builder.append(potonganBerwarna).append(penandaLanjut)
                txtKontenUtama.text = builder
                txtKontenUtama.visibility = View.VISIBLE
                wadahDinamisKonten.setBackgroundResource(0)
                wadahDinamisKonten.setPadding(0, 0, 0, 0)
                wadahHeaderShared.visibility = View.GONE
                txtKontenShared.visibility = View.GONE
            } else {
                // Kasus "shared status": SEKARANG ikut dipaginasi (lihat
                // ambilRencanaTeks) -- halaman ini bisa berisi salah satu,
                // gabungan, atau tak satu pun dari: potongan teks asli,
                // header kotak "Status Dibagikan", potongan teks shared,
                // tergantung di mana batas halaman jatuh. Penanda
                // "Selanjutnya >>" ditempel di teks shared kalau ada (karena
                // itu yg paling belakang tampil), kalau tidak ada baru di teks asli.
                val penandaUntukAsli = if (unit.rentangShared == null) penandaLanjut else ""
                val penandaUntukShared = if (unit.rentangShared != null) penandaLanjut else ""
                if (unit.rentangAsli != null) {
                    val r = unit.rentangAsli
                    val awal = r.first.coerceIn(0, kb.teksAsli.length)
                    val akhir = (r.last + 1).coerceIn(awal, kb.teksAsli.length)
                    val potongan = kb.teksAsli.substring(awal, akhir)
                    txtKontenUtama.text = if (!lanjutanSudahDipakai) {
                        lanjutanSudahDipakai = true
                        "\u21B3 (lanjutan halaman sebelumnya)\n\n$potongan$penandaUntukAsli"
                    } else "$potongan$penandaUntukAsli"
                    txtKontenUtama.visibility = View.VISIBLE
                } else {
                    txtKontenUtama.visibility = View.GONE
                }

                val kotakTampil = unit.headerShared || unit.rentangShared != null
                if (kotakTampil) {
                    val bantalanPx = (12 * context.resources.displayMetrics.density).toInt()
                    wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
                    wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)
                } else {
                    wadahDinamisKonten.setBackgroundResource(0)
                    wadahDinamisKonten.setPadding(0, 0, 0, 0)
                }

                wadahHeaderShared.visibility = if (unit.headerShared) View.VISIBLE else View.GONE
                if (unit.headerShared) txtNamaPemilikShared.text = kb.namaPemilik

                if (unit.rentangShared != null) {
                    val r = unit.rentangShared
                    val awal = r.first.coerceIn(0, kb.kontenShared.length)
                    val akhir = (r.last + 1).coerceIn(awal, kb.kontenShared.length)
                    val potongan = kb.kontenShared.substring(awal, akhir)
                    txtKontenShared.text = if (!lanjutanSudahDipakai) {
                        lanjutanSudahDipakai = true
                        "\u21B3 (lanjutan halaman sebelumnya)\n\n$potongan$penandaUntukShared"
                    } else "$potongan$penandaUntukShared"
                    txtKontenShared.visibility = View.VISIBLE
                } else {
                    txtKontenShared.visibility = View.GONE
                }
            }

            view.findViewById<TextView>(R.id.txtTanggal).text = arsip.tanggalBaca
            view.findViewById<TextView>(R.id.txtKategori).text = arsip.kategori
            // PERBAIKAN: dulu pakai nomor HALAMAN FISIK/total halaman fisik --
            // begitu 1 status kepecah jadi beberapa halaman, total ini ikut
            // membengkak (mis. 50 status jadi "53 halaman") padahal dari sudut
            // pandang pengguna tetap "50 status". Sekarang label pakai nomor
            // STATUS (arsip), bukan nomor halaman fisik -- beberapa halaman
            // lanjutan dari status yg sama akan menampilkan nomor yg SAMA.
            view.findViewById<TextView>(R.id.txtNomorHalaman).text = "Halaman : $nomorArsip/$totalArsip"
            view.findViewById<ImageView>(R.id.imgProfilAbah)?.setImageResource(R.drawable.profil_abah)

            // Profil (avatar+nama+tanggal+kategori) cuma tampil di halaman
            // PERTAMA arsip ini -- halaman lanjutan sudah ada tanda
            // "(lanjutan halaman sebelumnya)" sendiri di teksnya, jadi
            // profil tidak perlu diulang (hemat ruang, sesuai permintaan).
            // txtNomorHalaman TIDAK ikut disembunyikan -- tetap tampil semua halaman.
            view.findViewById<View>(R.id.wadahProfilPenulis).visibility = if (lanjutan) View.GONE else View.VISIBLE

            // Blok "Sumber Asli"/"Bagikan" dekoratif dihilangkan total dari
            // SEMUA halaman -- non-interaktif (cuma gambar di tekstur GL),
            // aksesnya yang sungguhan sudah ada lewat bar aksi baca di luar.
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
